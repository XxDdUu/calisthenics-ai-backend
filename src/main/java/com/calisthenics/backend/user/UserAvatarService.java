package com.calisthenics.backend.user;

import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.storage.AvatarImageProcessor;
import com.calisthenics.backend.storage.MinioAvatarStorageService;
import com.calisthenics.backend.user.dto.AvatarUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class UserAvatarService {

    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final AvatarImageProcessor imageProcessor;
    private final MinioAvatarStorageService storageService;

    @Transactional
    public AvatarUploadResponse upload(Long authenticatedUserId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "An avatar image is required.");
        }
        if (file.getSize() > AvatarImageProcessor.MAX_FILE_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Avatar images must be 5 MB or smaller.");
        }

        byte[] optimizedImage;
        try {
            optimizedImage = imageProcessor.process(file.getBytes());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The avatar image could not be read.");
        }

        String objectName = storageService.store(optimizedImage);
        try {
            String avatarUrl = storageService.publicUrl(objectName);
            User user = userRepository.findById(authenticatedUserId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
            user.setAvatarUrl(avatarUrl);
            userRepository.saveAndFlush(user);

            clientProfileRepository.findByUserId(authenticatedUserId).ifPresent(profile -> {
                profile.setAvatarUrl(avatarUrl);
                clientProfileRepository.saveAndFlush(profile);
            });
            coachProfileRepository.findByUserId(authenticatedUserId).ifPresent(profile -> {
                profile.setAvatarUrl(avatarUrl);
                coachProfileRepository.saveAndFlush(profile);
            });
            return new AvatarUploadResponse(avatarUrl);
        } catch (RuntimeException exception) {
            try {
                storageService.delete(objectName);
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }
    }
}
