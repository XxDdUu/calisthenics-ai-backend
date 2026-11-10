package com.calisthenics.backend.profile.entity;

public enum ProfileType {
    COACH,
    ATHLETE;

    public static ProfileType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String upper = value.trim().toUpperCase();
        if ("CLIENT".equals(upper)) {
            return ATHLETE;
        }
        return ProfileType.valueOf(upper);
    }
}
