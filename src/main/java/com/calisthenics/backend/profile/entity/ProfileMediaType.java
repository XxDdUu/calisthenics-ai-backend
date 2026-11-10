package com.calisthenics.backend.profile.entity;

public enum ProfileMediaType {
    IMAGE,
    VIDEO;

    public static ProfileMediaType fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return ProfileMediaType.valueOf(value.trim().toUpperCase());
    }
}
