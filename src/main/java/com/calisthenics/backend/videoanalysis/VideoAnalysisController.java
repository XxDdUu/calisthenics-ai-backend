package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.videoanalysis.dto.VideoAnalysisKeypointsResponseDto;
import com.calisthenics.backend.videoanalysis.dto.VideoAnalysisResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.net.URLConnection;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/v1/video-analysis")
@RequiredArgsConstructor
@Tag(name = "Video Analysis", description = "AI video scoring, keypoint visualization, and authenticated video streaming")
@SecurityRequirement(name = "Bearer Authentication")
public class VideoAnalysisController {

    private final VideoAnalysisService videoAnalysisService;
    private final MinioAnalysisMediaStorageService minioStorageService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload workout video for asynchronous AI pose scoring and keypoint visualization")
    public ResponseEntity<VideoAnalysisResponseDto> uploadVideo(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(value = "exerciseId", required = false) Long exerciseId,
            @RequestPart(value = "video", required = false) MultipartFile videoPart,
            @RequestPart(value = "file", required = false) MultipartFile filePart
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        MultipartFile file = videoPart != null ? videoPart : filePart;
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-empty video file part ('video' or 'file') is required.");
        }

        VideoAnalysisResponseDto response = videoAnalysisService.initiateVideoAnalysis(
                currentUser.getId(),
                exerciseId,
                file
        );
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get video analysis status, score metrics, and video streaming links")
    public ResponseEntity<VideoAnalysisResponseDto> getAnalysisById(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        VideoAnalysisResponseDto response = videoAnalysisService.getAnalysisById(id, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/keypoints")
    @Operation(summary = "Get full 17 COCO normalized keypoint timeline data for video player synchronization")
    public ResponseEntity<VideoAnalysisKeypointsResponseDto> getAnalysisKeypoints(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        VideoAnalysisKeypointsResponseDto response = videoAnalysisService.getAnalysisKeypoints(id, currentUser.getId(), isAdmin);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/history")
    @Operation(summary = "Get authenticated user's past video analysis history")
    public ResponseEntity<List<VideoAnalysisResponseDto>> getUserHistory(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        List<VideoAnalysisResponseDto> history = videoAnalysisService.getUserHistory(currentUser.getId());
        return ResponseEntity.ok(history);
    }

    @GetMapping("/{id}/original")
    @Operation(summary = "Stream original uploaded workout video with HTTP Range support (206 Partial Content)")
    public ResponseEntity<StreamingResponseBody> streamOriginalVideo(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        VideoAnalysis analysis = videoAnalysisService.findAndVerifyOwnership(id, currentUser.getId(), isAdmin);

        String objectKey = analysis.getOriginalVideoKey();
        return streamVideoFromMinio(objectKey, rangeHeader);
    }

    @GetMapping("/{id}/annotated")
    @Operation(summary = "Stream AI skeleton annotated video with HTTP Range support (206 Partial Content)")
    public ResponseEntity<StreamingResponseBody> streamAnnotatedVideo(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        VideoAnalysis analysis = videoAnalysisService.findAndVerifyOwnership(id, currentUser.getId(), isAdmin);

        String objectKey = analysis.getAnnotatedVideoKey();
        if (objectKey == null || objectKey.isBlank()) {
            if ("PROCESSING".equalsIgnoreCase(analysis.getStatus()) || "PENDING".equalsIgnoreCase(analysis.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Annotated video is still processing.");
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Annotated video is not available for this analysis.");
        }

        return streamVideoFromMinio(objectKey, rangeHeader);
    }

    private ResponseEntity<StreamingResponseBody> streamVideoFromMinio(String objectKey, String rangeHeader) {
        var stat = minioStorageService.stat(objectKey);
        long fileSize = stat.size();
        String contentType = stat.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(objectKey);
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = "video/mp4";
        }

        // 1. Full content request (no Range header)
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            StreamingResponseBody body = outputStream -> {
                try (var inputStream = minioStorageService.open(objectKey)) {
                    inputStream.transferTo(outputStream);
                } catch (IOException ex) {
                    log.debug("Client disconnected while streaming full video {}: {}", objectKey, ex.getMessage());
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + objectKey + "\"")
                    .body(body);
        }

        // 2. HTTP Range request handling (seeking support)
        String rangeValue = rangeHeader.substring(6).trim();
        long start;
        long end;

        try {
            if (rangeValue.startsWith("-")) {
                long suffixLength = Long.parseLong(rangeValue.substring(1));
                if (suffixLength <= 0) {
                    return rangeNotSatisfiable(fileSize);
                }
                start = Math.max(0, fileSize - suffixLength);
                end = fileSize - 1;
            } else {
                String[] parts = rangeValue.split("-", 2);
                start = Long.parseLong(parts[0].trim());
                if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                    end = Long.parseLong(parts[1].trim());
                } else {
                    end = fileSize - 1;
                }
            }

            if (start < 0 || start > end || start >= fileSize) {
                return rangeNotSatisfiable(fileSize);
            }
            if (end >= fileSize) {
                end = fileSize - 1;
            }
        } catch (NumberFormatException nfe) {
            return rangeNotSatisfiable(fileSize);
        }

        long contentLength = end - start + 1;
        StreamingResponseBody body = outputStream -> {
            try (var inputStream = minioStorageService.open(objectKey, start, contentLength)) {
                inputStream.transferTo(outputStream);
            } catch (IOException ex) {
                log.debug("Client disconnected while streaming video range {}: {}", objectKey, ex.getMessage());
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + objectKey + "\"")
                .body(body);
    }

    private ResponseEntity<StreamingResponseBody> rangeNotSatisfiable(long fileSize) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .build();
    }
}
