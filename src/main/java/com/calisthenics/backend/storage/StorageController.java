package com.calisthenics.backend.storage;

import com.calisthenics.backend.exercise.ExerciseMediaService;
import com.calisthenics.backend.exercise.ExerciseMediaType;
import com.calisthenics.backend.storage.dto.FileUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.security.access.prepost.PreAuthorize;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Storage", description = "Endpoints for uploading and serving media files")
public class StorageController {

    private final StorageService storageService;
    private final MinioAvatarStorageService avatarStorageService;
    private final MinioExerciseMediaStorageService exerciseMediaStorageService;
    private final ExerciseMediaService exerciseMediaService;

    @Value("${STORAGE_PROVIDER:local}")
    private String storageProvider;

    @PostMapping("/upload")
    @Operation(summary = "Upload image or video media file")
    public ResponseEntity<FileUploadResponse> uploadFile(@RequestParam("file") MultipartFile file) {
        String fileName = storageService.store(file);

        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/storage/files/")
                .path(fileName)
                .toUriString();

        FileUploadResponse response = FileUploadResponse.builder()
                .fileName(fileName)
                .fileDownloadUri(fileDownloadUri)
                .fileType(file.getContentType())
                .size(file.getSize())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/exercise-media/upload")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Upload an exercise image or video file")
    public ResponseEntity<FileUploadResponse> uploadExerciseMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long exerciseId,
            @RequestParam(required = false) Integer displayOrder
    ) {
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null ||
                !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a non-empty image or video file.");
        }
        if ((exerciseId == null && displayOrder != null) || (displayOrder != null && displayOrder < 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A non-negative display order requires an exercise ID.");
        }
        if (exerciseId != null) {
            exerciseMediaService.requireExercise(exerciseId);
        }

        boolean useMinio = "minio".equalsIgnoreCase(storageProvider);
        String fileName = useMinio ? exerciseMediaStorageService.store(file) : storageService.store(file);
        String mediaPath = useMinio
                ? "/api/v1/storage/exercise-media/"
                : "/api/v1/storage/files/";
        String fileDownloadUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(mediaPath)
                .path(fileName)
                .toUriString();

        FileUploadResponse.FileUploadResponseBuilder response = FileUploadResponse.builder()
                .fileName(fileName)
                .fileDownloadUri(fileDownloadUri)
                .fileType(file.getContentType())
                .size(file.getSize());

        if (exerciseId != null) {
            try {
                ExerciseMediaType mediaType = contentType.startsWith("image/")
                        ? ExerciseMediaType.IMAGE
                        : ExerciseMediaType.VIDEO;
                var mediaItem = exerciseMediaService.addMedia(
                        exerciseId,
                        mediaType,
                        fileDownloadUri,
                        displayOrder == null ? 0 : displayOrder
                );
                response.mediaItem(mediaItem);
                log.info("Exercise media upload completed: exerciseId={}, mediaId={}, type={}, displayOrder={}",
                        exerciseId, mediaItem.id(), mediaItem.type(), mediaItem.displayOrder());
            } catch (RuntimeException persistenceFailure) {
                try {
                    if (useMinio) {
                        exerciseMediaStorageService.delete(fileName);
                    } else {
                        storageService.delete(fileName);
                    }
                } catch (RuntimeException cleanupFailure) {
                    persistenceFailure.addSuppressed(cleanupFailure);
                    log.error("Failed to clean up exercise media after metadata persistence failed: exerciseId={}, objectName={}",
                            exerciseId, fileName, cleanupFailure);
                }
                log.error("Exercise media metadata persistence failed: exerciseId={}, objectName={}",
                        exerciseId, fileName, persistenceFailure);
                throw persistenceFailure;
            }
        }

        return ResponseEntity.ok(response.build());
    }

    @GetMapping("/files/{fileName:.+}")
    @Operation(summary = "Download or stream media file by filename with HTTP Range support")
    public ResponseEntity<StreamingResponseBody> getFile(
            @PathVariable String fileName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader,
            HttpServletRequest request
    ) {
        Resource resource = storageService.loadAsResource(fileName);
        long fileSize;
        try {
            fileSize = resource.contentLength();
        } catch (IOException e) {
            fileSize = -1L;
        }

        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (Exception ex) {
            // Default to binary stream if type cannot be determined
        }

        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(fileName);
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // Full content request (no Range header) or file size unknown
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=") || fileSize <= 0) {
            StreamingResponseBody body = outputStream -> {
                try (InputStream is = resource.getInputStream()) {
                    is.transferTo(outputStream);
                } catch (IOException e) {
                    log.debug("Client closed connection while downloading file {}: {}", fileName, e.getMessage());
                }
            };
            var responseBuilder = ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"");
            if (fileSize > 0) {
                responseBuilder.contentLength(fileSize);
            }
            return responseBuilder.body(body);
        }

        // HTTP Range request handling
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
            try (InputStream is = resource.getInputStream()) {
                long skipped = is.skip(start);
                long bytesRemaining = contentLength;
                byte[] buffer = new byte[8192];
                while (bytesRemaining > 0) {
                    int read = is.read(buffer, 0, (int) Math.min(buffer.length, bytesRemaining));
                    if (read == -1) break;
                    outputStream.write(buffer, 0, read);
                    bytesRemaining -= read;
                }
                outputStream.flush();
            } catch (IOException e) {
                log.debug("Client closed connection while streaming file {}: {}", fileName, e.getMessage());
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(body);
    }

    @GetMapping("/avatars/{objectName:.+}")
    @Operation(summary = "Download a public avatar image")
    public ResponseEntity<StreamingResponseBody> getAvatar(@PathVariable String objectName) {
        StreamingResponseBody body = outputStream -> {
            try (var inputStream = avatarStorageService.open(objectName)) {
                inputStream.transferTo(outputStream);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(body);
    }

    @GetMapping("/exercise-media/{objectName:.+}")
    @Operation(summary = "Download or stream an exercise media object from MinIO with HTTP Range support")
    public ResponseEntity<StreamingResponseBody> getExerciseMedia(
            @PathVariable String objectName,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        var stat = exerciseMediaStorageService.stat(objectName);
        long fileSize = stat.size();
        String contentType = stat.contentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(objectName);
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        // Full content request (no Range header)
        if (rangeHeader == null || !rangeHeader.startsWith("bytes=")) {
            StreamingResponseBody body = outputStream -> {
                try (var inputStream = exerciseMediaStorageService.open(objectName)) {
                    inputStream.transferTo(outputStream);
                }
            };
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(fileSize)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + objectName + "\"")
                    .body(body);
        }

        // Range request handling
        String rangeValue = rangeHeader.substring(6).trim();
        long start;
        long end;

        try {
            if (rangeValue.startsWith("-")) {
                // Suffix byte range: "-500" means last 500 bytes
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
            try (var inputStream = exerciseMediaStorageService.open(objectName, start, contentLength)) {
                inputStream.transferTo(outputStream);
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(contentLength)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + fileSize)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + objectName + "\"")
                .body(body);
    }

    private ResponseEntity<StreamingResponseBody> rangeNotSatisfiable(long fileSize) {
        return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                .header(HttpHeaders.CONTENT_RANGE, "bytes */" + fileSize)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .build();
    }
}
