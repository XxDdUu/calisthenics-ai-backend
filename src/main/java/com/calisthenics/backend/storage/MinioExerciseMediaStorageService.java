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

import java.io.InputStream;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
public class MinioExerciseMediaStorageService {

    private final MinioClient client;
    private final String bucket;

    public MinioExerciseMediaStorageService(
            @Value("${MINIO_ENDPOINT:}") String endpoint,
            @Value("${MINIO_ACCESS_KEY:}") String accessKey,
            @Value("${MINIO_SECRET_KEY:}") String secretKey,
            @Value("${MINIO_BUCKET_EXERCISE_MEDIA:exercise-media}") String bucket
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot upload an empty media file.");
        }
        String contentType = file.getContentType();
        if (contentType == null ||
                !(contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image and video files are supported.");
        }

        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        String suffix = extension != null && extension.matches("[A-Za-z0-9]{1,10}")
                ? "." + extension.toLowerCase(Locale.ROOT)
                : "";
        String objectName = UUID.randomUUID() + suffix;
        MinioClient minio = requireClient();

        try (InputStream input = file.getInputStream()) {
            minio.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .stream(input, file.getSize(), -1)
                    .contentType(contentType)
                    .build());
            return objectName;
        } catch (Exception exception) {
            try {
                minio.removeObject(RemoveObjectArgs.builder()
                        .bucket(bucket)
                        .object(objectName)
                        .build());
            } catch (Exception cleanupException) {
                exception.addSuppressed(cleanupException);
                log.error("Failed to clean up exercise media after storage failure: objectName={}",
                        objectName, cleanupException);
            }
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Exercise media storage is unavailable.",
                    exception
            );
        }
    }

    public void delete(String objectName) {
        String validName = validateObjectName(objectName);
        try {
            requireClient().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket)
                    .object(validName)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Could not remove an uncommitted exercise media object.", exception);
        }
    }

    public InputStream open(String objectName) {
        return open(objectName, null, null);
    }

    public InputStream open(String objectName, Long offset, Long length) {
        String validName = validateObjectName(objectName);
        try {
            GetObjectArgs.Builder builder = GetObjectArgs.builder()
                    .bucket(bucket)
                    .object(validName);
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
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise media was not found.");
            }
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    public StatObjectResponse stat(String objectName) {
        String validName = validateObjectName(objectName);
        try {
            return requireClient().statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(validName)
                    .build());
        } catch (ErrorResponseException exception) {
            if (exception.errorResponse() != null
                    && "NoSuchKey".equals(exception.errorResponse().code())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise media was not found.");
            }
            throw unavailable(exception);
        } catch (Exception exception) {
            throw unavailable(exception);
        }
    }

    public String contentType(String objectName) {
        return stat(objectName).contentType();
    }

    private MinioClient requireClient() {
        if (client == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Exercise media storage is not configured.");
        }
        return client;
    }

    private String validateObjectName(String objectName) {
        if (objectName == null ||
                !objectName.matches("[0-9a-fA-F-]{36}(\\.[A-Za-z0-9]{1,10})?")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise media was not found.");
        }
        return objectName;
    }

    private ResponseStatusException unavailable(Exception exception) {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Exercise media storage is unavailable.",
                exception
        );
    }
}
