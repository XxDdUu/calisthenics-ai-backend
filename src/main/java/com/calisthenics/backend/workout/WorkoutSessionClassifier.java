package com.calisthenics.backend.workout;

import java.time.LocalDate;
import java.time.ZoneId;

public final class WorkoutSessionClassifier {

    private WorkoutSessionClassifier() {}

    public static WorkoutSessionClassification classify(WorkoutSession session, LocalDate today, ZoneId zoneId) {
        if (session == null) {
            return WorkoutSessionClassification.CANCELLED;
        }
        LocalDate effectiveDate = resolveEffectiveDate(session, zoneId);
        return classify(effectiveDate, session.getStatus(), today);
    }

    public static WorkoutSessionClassification classify(
            LocalDate scheduledDate,
            WorkoutSessionStatus status,
            LocalDate today
    ) {
        if (status == WorkoutSessionStatus.COMPLETED) {
            return WorkoutSessionClassification.COMPLETED;
        }
        if (status == WorkoutSessionStatus.CANCELLED) {
            return WorkoutSessionClassification.CANCELLED;
        }
        if (scheduledDate != null && today != null && scheduledDate.isAfter(today)) {
            return WorkoutSessionClassification.UPCOMING;
        }
        return WorkoutSessionClassification.UNFINISHED;
    }

    public static boolean isUnfinished(WorkoutSession session, LocalDate today, ZoneId zoneId) {
        return classify(session, today, zoneId) == WorkoutSessionClassification.UNFINISHED;
    }

    public static boolean isUpcoming(WorkoutSession session, LocalDate today, ZoneId zoneId) {
        return classify(session, today, zoneId) == WorkoutSessionClassification.UPCOMING;
    }

    private static LocalDate resolveEffectiveDate(WorkoutSession session, ZoneId zoneId) {
        if (session.getWorkoutDate() != null) {
            return session.getWorkoutDate();
        }
        if (session.getCompletedAt() != null) {
            return session.getCompletedAt().withZoneSameInstant(zoneId).toLocalDate();
        }
        if (session.getStartedAt() != null) {
            return session.getStartedAt().withZoneSameInstant(zoneId).toLocalDate();
        }
        return null;
    }
}
