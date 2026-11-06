package com.calisthenics.backend.exercise;

import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
@Import(ExerciseMediaService.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ExerciseMediaPersistenceTest {

    @Autowired
    private ExerciseRepository exerciseRepository;
    @Autowired
    private ExerciseMediaItemRepository mediaItemRepository;
    @Autowired
    private ExerciseMediaService mediaService;

    @Test
    void addMedia_PersistsForeignKeyAndMediaFieldsForExerciseDetail() {
        Exercise exercise = exerciseRepository.saveAndFlush(Exercise.builder()
                .name("Media persistence test")
                .code("MEDIA_PERSISTENCE_TEST")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .active(true)
                .build());

        ExerciseMediaResponse saved = mediaService.addMedia(
                exercise.getId(),
                ExerciseMediaType.VIDEO,
                "https://api.example.com/api/v1/storage/exercise-media/"
                        + "123e4567-e89b-12d3-a456-426614174000.mp4",
                2
        );

        Exercise reloaded = exerciseRepository.findById(exercise.getId()).orElseThrow();
        ExerciseMediaResponse detailMedia = ExerciseMediaMapper.toResponses(reloaded).get(0);
        ExerciseMediaItem row = mediaItemRepository.findById(saved.id()).orElseThrow();

        assertEquals(exercise.getId(), row.getExercise().getId());
        assertEquals(1, mediaItemRepository.count());
        assertEquals(saved.id(), detailMedia.id());
        assertEquals(ExerciseMediaType.VIDEO, detailMedia.type());
        assertEquals(saved.url(), detailMedia.url());
        assertEquals(2, detailMedia.displayOrder());
    }
}
