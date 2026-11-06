package com.calisthenics.backend.exercise;

import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ExerciseMediaService {

    private final ExerciseRepository exerciseRepository;
    private final ExerciseMediaItemRepository mediaItemRepository;

    @Transactional(readOnly = true)
    public void requireExercise(Long exerciseId) {
        if (!exerciseRepository.existsById(exerciseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found.");
        }
    }

    @Transactional
    public ExerciseMediaResponse addMedia(
            Long exerciseId,
            ExerciseMediaType type,
            String url,
            int displayOrder
    ) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found."));
        ExerciseMediaItem mediaItem = mediaItemRepository.saveAndFlush(ExerciseMediaItem.builder()
                .exercise(exercise)
                .type(type)
                .url(url)
                .displayOrder(displayOrder)
                .build());
        exercise.getMediaItems().add(mediaItem);

        return new ExerciseMediaResponse(
                mediaItem.getId(),
                mediaItem.getType(),
                mediaItem.getUrl(),
                mediaItem.getThumbnailUrl(),
                mediaItem.getDisplayOrder()
        );
    }
}
