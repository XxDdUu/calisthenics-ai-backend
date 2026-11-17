package com.calisthenics.backend.adminai.type;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public enum AdminToolName {
    GET_USER_STATISTICS("get_user_statistics", true, false),
    GET_ALL_USERS("get_all_users", true, false),
    GET_USER_DETAILS("get_user_details", true, false),
    GET_USER_WORKOUT_HISTORY("get_user_workout_history", true, false),
    GET_USER_PERSONAL_RECORDS("get_user_personal_records", true, false),
    GET_USER_PROGRESS("get_user_progress", true, false),
    GET_USER_ACTIVITY("get_user_activity", true, false),
    GET_COACH_CLIENT_STATISTICS("get_coach_client_statistics", true, false),
    GET_PLATFORM_ENGAGEMENT("get_platform_engagement", true, false),
    ANALYZE_USER_SEGMENTS("analyze_user_segments", true, false),
    GENERATE_ADMIN_RECOMMENDATIONS("generate_admin_recommendations", false, false),
    CREATE_PROGRAM("createProgram", false, false, "create_program"),
    UPDATE_PROGRAM("updateProgram", false, false, "update_program"),
    PREVIEW_PROGRAM("previewProgram", true, false, "preview_program"),
    PUBLISH_PROGRAM("publishProgram", false, true, "publish_program"),
    UNPUBLISH_PROGRAM("unpublishProgram", false, true, "unpublish_program"),
    CONFIGURE_AUDIENCE("configureAudience", false, false, "configure_audience"),
    GET_ACTIVE_CAMPAIGNS("getActiveCampaigns", true, false, "get_active_campaigns"),
    GET_CAMPAIGN_ANALYTICS("getCampaignAnalytics", true, false, "get_campaign_analytics"),
    GRANT_PRO("grantPro", false, true, "grant_pro"),
    REVOKE_PRO("revokePro", false, true, "revoke_pro"),
    CHECK_PRO_STATUS("checkProStatus", true, false, "check_pro_status", "get_user_pro_status");

    private final String toolName;
    private final boolean readOnly;
    private final boolean confirmationRequired;
    private final List<String> aliases;

    AdminToolName(String toolName, boolean readOnly, boolean confirmationRequired, String... aliases) {
        this.toolName = toolName;
        this.readOnly = readOnly;
        this.confirmationRequired = confirmationRequired;
        this.aliases = Arrays.asList(aliases);
    }

    public String getToolName() {
        return toolName;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    public static Optional<AdminToolName> fromWireName(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.trim();
        String compact = normalized.replace("_", "").replace("-", "").toLowerCase(Locale.US);
        return Arrays.stream(values())
                .filter(v -> v.toolName.equalsIgnoreCase(normalized)
                        || v.name().equalsIgnoreCase(normalized)
                        || v.aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized))
                        || v.toolName.toLowerCase(Locale.US).equals(compact))
                .findFirst();
    }
}
