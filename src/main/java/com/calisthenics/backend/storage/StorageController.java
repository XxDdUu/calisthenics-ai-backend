package com.calisthenics.backend.storage;

import com.calisthenics.backend.storage.dto.FileUploadResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
import java.net.URLConnection;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
@Tag(name = "Storage", description = "Endpoints for uploading and serving media files")
public class StorageController {

    private final StorageService storageService;
    private final MinioAvatarStorageService avatarStorageService;
    private final MinioExerciseMediaStorageService exerciseMediaStorageService;

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
    public ResponseEntity<FileUploadResponse> uploadExerciseMedia(@RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        if (file.isEmpty() || contentType == null ||
                !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select a non-empty image or video file.");
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

        return ResponseEntity.ok(FileUploadResponse.builder()
                .fileName(fileName)
                .fileDownloadUri(fileDownloadUri)
                .fileType(file.getContentType())
                .size(file.getSize())
                .build());
    }

    @GetMapping("/files/{fileName:.+}")
    @Operation(summary = "Download or stream media file by filename")
    public ResponseEntity<Resource> getFile(@PathVariable String fileName, HttpServletRequest request) {
        Resource resource = storageService.loadAsResource(fileName);

        String contentType = null;
        try {
            contentType = request.getServletContext().getMimeType(resource.getFile().getAbsolutePath());
        } catch (IOException ex) {
            // Default to binary stream if type cannot be determined
        }

        if (contentType == null) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
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
    @Operation(summary = "Download or stream an exercise media object from MinIO")
    public ResponseEntity<StreamingResponseBody> getExerciseMedia(@PathVariable String objectName) {
        String contentType = exerciseMediaStorageService.contentType(objectName);
        StreamingResponseBody body = outputStream -> {
            try (var inputStream = exerciseMediaStorageService.open(objectName)) {
                inputStream.transferTo(outputStream);
            }
        };
        if (contentType == null || contentType.isBlank()) {
            contentType = URLConnection.guessContentTypeFromName(objectName);
        }
        if (contentType == null || contentType.isBlank()) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + objectName + "\"")
                .body(body);
    }
}
