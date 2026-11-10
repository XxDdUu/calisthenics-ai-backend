package com.calisthenics.backend.profile.controller;

import com.calisthenics.backend.profile.dto.request.ReorderProfileMediaRequest;
import com.calisthenics.backend.profile.dto.response.ProfileMediaResponse;
import com.calisthenics.backend.profile.entity.ProfileMediaType;
import com.calisthenics.backend.profile.entity.ProfileType;
import com.calisthenics.backend.profile.service.ProfileMediaService;
import com.calisthenics.backend.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileMediaControllerTest {

    @Mock
    private ProfileMediaService profileMediaService;

    @InjectMocks
    private ProfileMediaController controller;

    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        testUser = new UserPrincipal(1L, "user@test.com", "Test User", "password", Collections.emptyList());
    }

    @Test
    void getProfileMedia_shouldReturnMediaList() {
        ProfileMediaResponse item = ProfileMediaResponse.builder()
                .id(1L)
                .userId(1L)
                .profileType(ProfileType.COACH)
                .mediaType(ProfileMediaType.IMAGE)
                .url("/files/test.jpg")
                .build();

        when(profileMediaService.getMedia(1L, ProfileType.COACH)).thenReturn(List.of(item));

        ResponseEntity<List<ProfileMediaResponse>> response = controller.getProfileMedia(1L, "COACH");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("/files/test.jpg", response.getBody().get(0).getUrl());
    }

    @Test
    void uploadMedia_shouldCallService() {
        MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", "bytes".getBytes());
        ProfileMediaResponse resp = ProfileMediaResponse.builder().id(5L).url("/files/image.png").build();

        when(profileMediaService.uploadMedia(1L, ProfileType.ATHLETE, file, null)).thenReturn(resp);

        ResponseEntity<ProfileMediaResponse> result = controller.uploadMedia(testUser, "ATHLETE", file, null);

        assertEquals(HttpStatus.CREATED, result.getStatusCode());
        assertEquals(5L, result.getBody().getId());
        verify(profileMediaService).uploadMedia(1L, ProfileType.ATHLETE, file, null);
    }

    @Test
    void deleteMedia_shouldCallService() {
        doNothing().when(profileMediaService).deleteMedia(1L, 10L);

        ResponseEntity<Void> response = controller.deleteMedia(testUser, 10L);

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        verify(profileMediaService).deleteMedia(1L, 10L);
    }

    @Test
    void reorderMedia_shouldCallService() {
        ReorderProfileMediaRequest req = ReorderProfileMediaRequest.builder()
                .profileType(ProfileType.COACH)
                .mediaIds(List.of(2L, 1L))
                .build();
        when(profileMediaService.reorderMedia(1L, req)).thenReturn(List.of());

        ResponseEntity<List<ProfileMediaResponse>> response = controller.reorderMedia(testUser, req);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(profileMediaService).reorderMedia(1L, req);
    }

    @Test
    void setPrimaryCover_shouldCallService() {
        when(profileMediaService.setPrimaryCover(1L, 7L)).thenReturn(List.of());

        ResponseEntity<List<ProfileMediaResponse>> response = controller.setPrimaryCover(testUser, 7L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(profileMediaService).setPrimaryCover(1L, 7L);
    }
}
