package com.calisthenics.backend.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
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
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Exercise media storage is unavailable.",
                    exception
            );
        }
    }

    public InputStream open(String objectName) {
        String validName = validateObjectName(objectName);
        try {
            return requireClient().getObject(GetObjectArgs.builder()
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
        String validName = validateObjectName(objectName);
        try {
            StatObjectResponse metadata = requireClient().statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(validName)
                    .build());
            return metadata.contentType();
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
