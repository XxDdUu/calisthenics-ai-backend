package com.calisthenics.backend.profile.service;

import com.calisthenics.backend.profile.dto.request.ReorderProfileMediaRequest;
import com.calisthenics.backend.profile.dto.response.ProfileMediaResponse;
import com.calisthenics.backend.profile.entity.ProfileMediaItem;
import com.calisthenics.backend.profile.entity.ProfileMediaType;
import com.calisthenics.backend.profile.entity.ProfileType;
import com.calisthenics.backend.profile.repository.ProfileMediaItemRepository;
import com.calisthenics.backend.profile.service.impl.ProfileMediaServiceImpl;
import com.calisthenics.backend.storage.MinioExerciseMediaStorageService;
import com.calisthenics.backend.storage.StorageService;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileMediaServiceTest {

    @Mock
    private ProfileMediaItemRepository profileMediaItemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StorageService storageService;

    @Mock
    private MinioExerciseMediaStorageService exerciseMediaStorageService;

    @InjectMocks
    private ProfileMediaServiceImpl profileMediaService;

    private User testUser;
    private User otherUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).fullName("Coach Alice").build();
        otherUser = User.builder().id(2L).fullName("Bob").build();
        ReflectionTestUtils.setField(profileMediaService, "storageProvider", "local");
    }

    @Test
    void getMedia_shouldReturnOrderedMedia() {
        ProfileMediaItem item1 = ProfileMediaItem.builder()
                .id(10L)
                .user(testUser)
                .profileType(ProfileType.COACH)
                .mediaType(ProfileMediaType.IMAGE)
                .url("/files/1.jpg")
                .displayOrder(0)
                .build();
        ProfileMediaItem item2 = ProfileMediaItem.builder()
                .id(11L)
                .user(testUser)
                .profileType(ProfileType.COACH)
                .mediaType(ProfileMediaType.VIDEO)
                .url("/files/2.mp4")
                .displayOrder(1)
                .build();

        when(profileMediaItemRepository.findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(1L, ProfileType.COACH))
                .thenReturn(List.of(item1, item2));

        List<ProfileMediaResponse> result = profileMediaService.getMedia(1L, ProfileType.COACH);

        assertEquals(2, result.size());
        assertEquals(10L, result.get(0).getId());
        assertEquals(ProfileMediaType.IMAGE, result.get(0).getMediaType());
        assertEquals(11L, result.get(1).getId());
        assertEquals(ProfileMediaType.VIDEO, result.get(1).getMediaType());
    }

    @Test
    void uploadMedia_image_shouldStoreAndAssignNextDisplayOrder() {
        MockMultipartFile file = new MockMultipartFile("file", "pic.jpg", "image/jpeg", "image bytes".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(storageService.store(file)).thenReturn("uuid-pic.jpg");
        when(profileMediaItemRepository.countByUserIdAndProfileType(1L, ProfileType.COACH)).thenReturn(2);
        when(profileMediaItemRepository.save(any(ProfileMediaItem.class))).thenAnswer(inv -> {
            ProfileMediaItem item = inv.getArgument(0);
            item.setId(99L);
            return item;
        });

        ProfileMediaResponse response = profileMediaService.uploadMedia(1L, ProfileType.COACH, file, null);

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals(ProfileType.COACH, response.getProfileType());
        assertEquals(ProfileMediaType.IMAGE, response.getMediaType());
        assertEquals(2, response.getDisplayOrder());
        verify(storageService).store(file);
    }

    @Test
    void deleteMedia_shouldCheckOwnership_andReindex() {
        ProfileMediaItem item1 = ProfileMediaItem.builder()
                .id(10L)
                .user(testUser)
                .profileType(ProfileType.COACH)
                .displayOrder(0)
                .build();
        ProfileMediaItem item2 = ProfileMediaItem.builder()
                .id(11L)
                .user(testUser)
                .profileType(ProfileType.COACH)
                .displayOrder(1)
                .build();

        when(profileMediaItemRepository.findById(10L)).thenReturn(Optional.of(item1));
        when(profileMediaItemRepository.findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(1L, ProfileType.COACH))
                .thenReturn(new ArrayList<>(List.of(item2)));

        profileMediaService.deleteMedia(1L, 10L);

        verify(profileMediaItemRepository).delete(item1);
        assertEquals(0, item2.getDisplayOrder());
        verify(profileMediaItemRepository).saveAll(anyList());
    }

    @Test
    void deleteMedia_forbiddenWhenNotOwner() {
        ProfileMediaItem item = ProfileMediaItem.builder()
                .id(10L)
                .user(otherUser)
                .profileType(ProfileType.COACH)
                .build();

        when(profileMediaItemRepository.findById(10L)).thenReturn(Optional.of(item));

        assertThrows(ResponseStatusException.class, () -> profileMediaService.deleteMedia(1L, 10L));
        verify(profileMediaItemRepository, never()).delete(any());
    }

    @Test
    void reorderMedia_shouldUpdateOrders() {
        ProfileMediaItem item1 = ProfileMediaItem.builder().id(10L).user(testUser).profileType(ProfileType.ATHLETE).displayOrder(0).build();
        ProfileMediaItem item2 = ProfileMediaItem.builder().id(11L).user(testUser).profileType(ProfileType.ATHLETE).displayOrder(1).build();

        when(profileMediaItemRepository.findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(1L, ProfileType.ATHLETE))
                .thenReturn(new ArrayList<>(List.of(item1, item2)));
        when(profileMediaItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ReorderProfileMediaRequest request = ReorderProfileMediaRequest.builder()
                .profileType(ProfileType.ATHLETE)
                .mediaIds(Arrays.asList(11L, 10L))
                .build();

        List<ProfileMediaResponse> result = profileMediaService.reorderMedia(1L, request);

        assertEquals(2, result.size());
        assertEquals(11L, result.get(0).getId());
        assertEquals(0, result.get(0).getDisplayOrder());
        assertEquals(10L, result.get(1).getId());
        assertEquals(1, result.get(1).getDisplayOrder());
    }

    @Test
    void setPrimaryCover_shouldSetSelectedToZero() {
        ProfileMediaItem item1 = ProfileMediaItem.builder().id(10L).user(testUser).profileType(ProfileType.COACH).displayOrder(0).build();
        ProfileMediaItem item2 = ProfileMediaItem.builder().id(11L).user(testUser).profileType(ProfileType.COACH).displayOrder(1).build();

        when(profileMediaItemRepository.findById(11L)).thenReturn(Optional.of(item2));
        when(profileMediaItemRepository.findByUserIdAndProfileTypeOrderByDisplayOrderAscIdAsc(1L, ProfileType.COACH))
                .thenReturn(new ArrayList<>(List.of(item1, item2)));
        when(profileMediaItemRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        List<ProfileMediaResponse> result = profileMediaService.setPrimaryCover(1L, 11L);

        assertEquals(2, result.size());
        assertEquals(11L, result.get(0).getId());
        assertEquals(0, result.get(0).getDisplayOrder());
        assertEquals(10L, result.get(1).getId());
        assertEquals(1, result.get(1).getDisplayOrder());
    }
}
