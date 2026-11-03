package com.calisthenics.backend.analytics.model;

public enum PersonalRecordType {
    MAX_WEIGHT("kg"),
    MAX_REPS("reps"),
    MAX_HOLD("seconds"),
    MAX_DISTANCE("km"),
    MAX_DURATION("seconds"),
    BODYWEIGHT("reps"),
    MAX_VOLUME("kg");

    private final String defaultUnit;

    PersonalRecordType(String defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }
}
