package com.calisthenics.backend.exercise;

import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ExerciseMediaMapper {

    private ExerciseMediaMapper() {
    }

    public static List<ExerciseMediaResponse> toResponses(Exercise exercise) {
        List<ExerciseMediaResponse> responses = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int nextOrder = 0;

        List<ExerciseMediaItem> orderedItems = exercise.getMediaItems().stream()
                .sorted(Comparator.comparingInt(ExerciseMediaItem::getDisplayOrder)
                        .thenComparing(item -> item.getId() == null ? Long.MAX_VALUE : item.getId()))
                .toList();
        for (ExerciseMediaItem item : orderedItems) {
            if (seen.add(key(item.getType(), item.getUrl()))) {
                responses.add(new ExerciseMediaResponse(
                        item.getId(),
                        item.getType(),
                        item.getUrl(),
                        item.getThumbnailUrl(),
                        item.getDisplayOrder()
                ));
            }
            nextOrder = Math.max(nextOrder, item.getDisplayOrder() + 1);
        }

        appendLegacy(responses, seen, ExerciseMediaType.IMAGE, exercise.getImageUrl(), nextOrder++);
        appendLegacy(responses, seen, ExerciseMediaType.VIDEO, exercise.getVideoUrl(), nextOrder);
        return List.copyOf(responses);
    }

    private static void appendLegacy(
            List<ExerciseMediaResponse> responses,
            Set<String> seen,
            ExerciseMediaType type,
            String url,
            int displayOrder
    ) {
        if (url != null && !url.isBlank() && seen.add(key(type, url))) {
            responses.add(new ExerciseMediaResponse(null, type, url, null, displayOrder));
        }
    }

    private static String key(ExerciseMediaType type, String url) {
        return type + ":" + url;
    }
}
