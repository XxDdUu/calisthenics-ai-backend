package com.calisthenics.backend.workoutplan.type;

/**
 * Optional suggested categories for a weekly-split day. A custom workout name is never constrained
 * by this enum and may be supplied with any category (or no category at all).
 */
public enum WorkoutSessionType {
    PUSH,
    PULL,
    LEGS,
    UPPER,
    LOWER,
    FULL_BODY,
    BENT_ARM,
    STRAIGHT_ARM,
    PLANCHE,
    FRONT_LEVER,
    HANDSTAND,
    SKILL_TRAINING,
    STRENGTH,
    HYPERTROPHY,
    MOBILITY,
    CONDITIONING,
    OTHER,
    CUSTOM,
    REST
}
