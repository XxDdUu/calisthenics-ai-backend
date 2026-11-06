package com.calisthenics.backend.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;

@Service
public class MinioAvatarStorageService {

    private final MinioClient client;
    private final String bucket;
    private final String externalUrl;

    public MinioAvatarStorageService(
            @Value("${MINIO_ENDPOINT:}") String endpoint,
            @Value("${MINIO_ACCESS_KEY:}") String accessKey,
            @Value("${MINIO_SECRET_KEY:}") String secretKey,
            @Value("${MINIO_BUCKET_AVATARS:avatars}") String bucket,
            @Value("${MINIO_EXTERNAL_URL:}") String externalUrl
    ) {
        this.bucket = bucket;
        this.externalUrl = externalUrl == null ? "" : externalUrl.replaceAll("/+$", "");
        this.client = endpoint == null || endpoint.isBlank() ||
                accessKey == null || accessKey.isBlank() ||
                secretKey == null || secretKey.isBlank()
                ? null
                : MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();
    }

    public String store(byte[] image) {
        MinioClient minio = requireClient();
        String objectName = UUID.randomUUID() + ".jpg";
        try {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(new ByteArrayInputStream(image), image.length, -1)
                    .contentType("image/jpeg")
                    .build());
            return objectName;
        } catch (Exception exception) {
            try {
                minio.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Avatar storage is unavailable.",
                    exception
            );
        }
    }

    public InputStream open(String objectName) {
        MinioClient minio = requireClient();
        String validatedObjectName = validateObjectName(objectName);
        try {
            return minio.getObject(GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(validatedObjectName)
                    .build());
        } catch (ErrorResponseException exception) {
            if (exception.errorResponse() != null &&
                    "NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar image was not found.");
            }
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Avatar storage is unavailable.",
                    exception
            );
        } catch (Exception exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Avatar storage is unavailable.",
                    exception
            );
        }
    }

    public void delete(String objectName) {
        MinioClient minio = requireClient();
        String validatedObjectName = validateObjectName(objectName);
        try {
            minio.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(validatedObjectName)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not remove an uncommitted avatar object.", exception);
        }
    }

    public String publicUrl(String objectName) {
        if (externalUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Public avatar URL is not configured.");
        }
        return externalUrl + "/avatars/" + validateObjectName(objectName);
    }

    private MinioClient requireClient() {
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Avatar storage is not configured.");
        }
        return client;
    }

    private String validateObjectName(String objectName) {
        if (objectName == null || !objectName.matches("[0-9a-fA-F-]{36}\\.jpg")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Avatar image was not found.");
        }
        return objectName;
    }
}
