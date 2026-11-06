package com.calisthenics.backend.user;

import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.storage.AvatarImageProcessor;
import com.calisthenics.backend.storage.MinioAvatarStorageService;
import com.calisthenics.backend.user.dto.AvatarUploadResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Optional;

import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAvatarServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ClientProfileRepository clientProfileRepository;
    @Mock
    private CoachProfileRepository coachProfileRepository;
    @Mock
    private MinioAvatarStorageService storageService;
    @Mock
    private AvatarImageProcessor imageProcessor;

    @InjectMocks
    private UserAvatarService userAvatarService;

    @Test
    void updatesAuthenticatedUsersAvatarAndReturnsPublicUrl() throws Exception {
        long authenticatedUserId = 29L;
        User user = User.builder().id(authenticatedUserId).email("athlete@example.com").fullName("Athlete").build();
        String objectName = "8f88093e-e6d9-4bf8-8112-c17fcb81c85b.jpg";
        String publicUrl = "https://api.example.test/api/v1/storage/avatars/" + objectName;
        when(storageService.store(any())).thenReturn(objectName);
        when(imageProcessor.process(any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.publicUrl(objectName)).thenReturn(publicUrl);
        when(userRepository.findById(authenticatedUserId)).thenReturn(Optional.of(user));
        when(userRepository.saveAndFlush(user)).thenReturn(user);
        ClientProfile clientProfile = ClientProfile.builder().user(user).build();
        CoachProfile coachProfile = CoachProfile.builder().user(user).build();
        when(clientProfileRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(clientProfile));
        when(coachProfileRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(coachProfile));
        when(clientProfileRepository.saveAndFlush(clientProfile)).thenReturn(clientProfile);
        when(coachProfileRepository.saveAndFlush(coachProfile)).thenReturn(coachProfile);

        AvatarUploadResponse result = userAvatarService.upload(
                authenticatedUserId,
                new MockMultipartFile("file", "avatar.png", "image/png", createPng())
        );

        assertEquals(publicUrl, result.getAvatarUrl());
        assertEquals(publicUrl, user.getAvatarUrl());
        assertEquals(publicUrl, clientProfile.getAvatarUrl());
        assertEquals(publicUrl, coachProfile.getAvatarUrl());
        verify(userRepository).saveAndFlush(user);
    }

    @Test
    void removesUploadedObjectWhenProfileUpdateCannotBeCompleted() throws Exception {
        long authenticatedUserId = 29L;
        String objectName = "8f88093e-e6d9-4bf8-8112-c17fcb81c85b.jpg";
        when(storageService.store(any())).thenReturn(objectName);
        when(imageProcessor.process(any())).thenReturn(new byte[]{1, 2, 3});
        when(storageService.publicUrl(objectName)).thenReturn("https://api.example.test/avatar");
        when(userRepository.findById(authenticatedUserId)).thenReturn(Optional.empty());

        assertThrows(
                ResponseStatusException.class,
                () -> userAvatarService.upload(
                        authenticatedUserId,
                        new MockMultipartFile("file", "avatar.png", "image/png", createPng())
                )
        );

        verify(storageService).delete(objectName);
    }

    private byte[] createPng() throws Exception {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        image.flush();
        return output.toByteArray();
    }
}
