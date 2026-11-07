package com.calisthenics.backend.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class MinioAnalysisMediaStorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioAnalysisMediaStorageService(
            @Value("${MINIO_ENDPOINT:}") String endpoint,
            @Value("${MINIO_ACCESS_KEY:}") String accessKey,
            @Value("${MINIO_SECRET_KEY:}") String secretKey,
            @Value("${MINIO_BUCKET_ANALYSIS_MEDIA:analysis-media}") String bucket
    ) {
        this.bucket = bucket;
        this.client = endpoint == null || endpoint.isBlank()
                || accessKey == null || accessKey.isBlank()
                || secretKey == null || secretKey.isBlank()
                ? null
                : MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
    }

    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload an empty video file.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("video/")) {
            contentType = "video/mp4";
        }

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String suffix = extension != null && extension.matches("[A-Za-z0-9]{1,10}")
                ? "." + extension.toLowerCase(Locale.ROOT)
                : ".mp4";
        String objectName = UUID.randomUUID() + "_orig" + suffix;
        MinioClient minio = requireClient();

        try (InputStream input = file.getInputStream()) {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            log.info("Uploaded video to MinIO bucket={}, objectName={}", bucket, objectName);
            return objectName;
        } catch (Exception exception) {
            try {
                minio.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            log.error("Failed to upload video to MinIO: objectName={}", objectName, exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Analysis media storage is unavailable.",
                    exception
            );
        }
    }

    public void storeWithKey(InputStream input, long size, String contentType, String objectName) {
        MinioClient minio = requireClient();
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, size, -1)
                    .contentType(contentType != null ? contentType : "video/mp4")
                    .build());
            log.info("Stored object in MinIO bucket={}, objectName={}, size={}", bucket, objectName, size);
        } catch (Exception exception) {
            log.error("Failed to store stream in MinIO objectName={}", objectName, exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Failed to store video in analysis storage.",
                    exception
            );
        }
    }

    public void storeBytes(byte[] data, String contentType, String objectName) {
        try (InputStream bais = new ByteArrayInputStream(data)) {
            storeWithKey(bais, data.length, contentType, objectName);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to write video bytes", e);
        }
    }

    public InputStream open(String objectName) {
        return open(objectName, null, null);
    }

    public InputStream open(String objectName, Long offset, Long length) {
        validateObjectName(objectName);
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName);
            if (offset != null) {
                builder.offset(offset);
            }
            if (length != null) {
                builder.length(length);
            }
            return requireClient().getObject(builder.build());
        } catch (ErrorResponseException exception) {
            if (exception.errorResponse() != null
                    && "NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video was not found.");
            }
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    public StatObjectResponse stat(String objectName) {
        validateObjectName(objectName);
        try {
            return requireClient().statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (ErrorResponseException exception) {
            if (exception.errorResponse() != null
                    && "NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Video was not found.");
            }
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    public void delete(String objectName) {
        validateObjectName(objectName);
        try {
            requireClient().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
        } catch (Exception exception) {
            log.error("Could not remove analysis media object {}", objectName, exception);
        }
    }

    private MinioClient requireClient() {
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Analysis media storage is not configured.");
        }
        return client;
    }

    private void validateObjectName(String objectName) {
        if (objectName == null || objectName.isBlank() || objectName.contains("..") || objectName.contains("/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid object key.");
        }
    }

    private ResponseStatusException unavailable(Exception exception) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Analysis media storage is unavailable.",
                exception
        );
    }
}
