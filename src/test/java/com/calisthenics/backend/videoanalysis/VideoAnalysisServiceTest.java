package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.analysis.AiServiceClient;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.videoanalysis.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VideoAnalysisServiceTest {

    @Mock
    private VideoAnalysisRepository videoAnalysisRepository;

    @Mock
    private VideoAnalysisKeypointRepository keypointRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private MinioAnalysisMediaStorageService minioStorageService;

    @Mock
    private AiServiceClient aiServiceClient;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Spy
    private TaskExecutor taskExecutor = new SyncTaskExecutor();

    private VideoAnalysisService service;

    private User testUser;
    private Exercise testExercise;

    @BeforeEach
    void setUp() {
        service = new VideoAnalysisService(
                videoAnalysisRepository,
                keypointRepository,
                userRepository,
                exerciseRepository,
                minioStorageService,
                aiServiceClient,
                objectMapper,
                taskExecutor
        );

        testUser = User.builder().id(1L).email("user@test.com").role(Role.ROLE_USER).build();
        testExercise = Exercise.builder().id(5L).name("Planche").code("PLANCHE").build();
    }

    @Test
    void initiateVideoAnalysis_Success() {
        MockMultipartFile file = new MockMultipartFile("video", "test.mp4", "video/mp4", "dummy content".getBytes());

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(exerciseRepository.findById(5L)).thenReturn(Optional.of(testExercise));
        when(minioStorageService.store(any())).thenReturn("uuid_orig.mp4");

        VideoAnalysis savedEntity = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .exercise(testExercise)
                .originalVideoKey("uuid_orig.mp4")
                .status("PENDING")
                .build();
        when(videoAnalysisRepository.save(any())).thenReturn(savedEntity);

        VideoAnalysisResponseDto response = service.initiateVideoAnalysis(1L, 5L, file);

        assertNotNull(response);
        assertEquals(10L, response.getId());
        assertEquals("PENDING", response.getStatus());
        verify(minioStorageService).store(file);
    }

    @Test
    void completeAnalysis_SavesScoresAndKeypoints() {
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .exercise(testExercise)
                .originalVideoKey("uuid_orig.mp4")
                .status("PROCESSING")
                .build();
        when(videoAnalysisRepository.findById(10L)).thenReturn(Optional.of(analysis));

        AiVideoScoringResponseDto aiResult = AiVideoScoringResponseDto.builder()
                .analysisId("test-uuid")
                .status("COMPLETED")
                .score(85.5)
                .formAccuracy(90.0)
                .stability(82.0)
                .alignment(84.0)
                .video(AiVideoOutputMetadataDto.builder()
                        .durationMs(5000L)
                        .width(1920)
                        .height(1080)
                        .fps(30.0)
                        .build())
                .metrics(Map.of("elbow_extension", 175.0))
                .feedback(List.of("Lock elbows completely"))
                .keypoints(AiVideoKeypointsDataDto.builder()
                        .format("COCO_17")
                        .frames(List.of(
                                AiVideoKeypointFrameDto.builder()
                                        .frameIndex(0)
                                        .timestampMs(0L)
                                        .keypoints(List.of(
                                                AiVideoKeypointItemDto.builder()
                                                        .name("left_shoulder")
                                                        .x(0.5f)
                                                        .y(0.3f)
                                                        .confidence(0.9f)
                                                        .build()
                                        ))
                                        .build()
                        ))
                        .build())
                .build();

        service.completeAnalysis(10L, aiResult, "uuid_annot.mp4");

        assertEquals("COMPLETED", analysis.getStatus());
        assertEquals(BigDecimal.valueOf(85.5), analysis.getOverallScore());
        assertEquals("uuid_annot.mp4", analysis.getAnnotatedVideoKey());
        verify(keypointRepository).saveAll(anyList());
        verify(videoAnalysisRepository).save(analysis);
    }

    @Test
    void findAndVerifyOwnership_ThrowsForbiddenWhenDifferentUser() {
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .build();
        when(videoAnalysisRepository.findById(10L)).thenReturn(Optional.of(analysis));

        assertThrows(ResponseStatusException.class, () ->
                service.findAndVerifyOwnership(10L, 999L, false));
    }

    @Test
    void findAndVerifyOwnership_AllowsAdminAccess() {
        VideoAnalysis analysis = VideoAnalysis.builder()
                .id(10L)
                .user(testUser)
                .build();
        when(videoAnalysisRepository.findById(10L)).thenReturn(Optional.of(analysis));

        VideoAnalysis result = service.findAndVerifyOwnership(10L, 999L, true);
        assertNotNull(result);
        assertEquals(10L, result.getId());
    }
}
