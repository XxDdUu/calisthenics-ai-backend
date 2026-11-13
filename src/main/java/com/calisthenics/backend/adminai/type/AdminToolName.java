package com.calisthenics.backend.adminai.type;

import java.util.Arrays;
import java.util.Optional;

public enum AdminToolName {
    GET_USER_STATISTICS("get_user_statistics"),
    GET_ALL_USERS("get_all_users"),
    GET_USER_DETAILS("get_user_details"),
    GET_USER_WORKOUT_HISTORY("get_user_workout_history"),
    GET_USER_PERSONAL_RECORDS("get_user_personal_records"),
    GET_USER_PROGRESS("get_user_progress"),
    GET_USER_ACTIVITY("get_user_activity"),
    GET_COACH_CLIENT_STATISTICS("get_coach_client_statistics"),
    GET_PLATFORM_ENGAGEMENT("get_platform_engagement"),
    ANALYZE_USER_SEGMENTS("analyze_user_segments"),
    GENERATE_ADMIN_RECOMMENDATIONS("generate_admin_recommendations");

    private final String toolName;

    AdminToolName(String toolName) {
        this.toolName = toolName;
    }

    public String getToolName() {
        return toolName;
    }

    public static Optional<AdminToolName> fromWireName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim();
        return Arrays.stream(values())
                .filter(v -> v.toolName.equalsIgnoreCase(normalized) || v.name().equalsIgnoreCase(normalized))
                .findFirst();
    }
}
