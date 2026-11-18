package com.calisthenics.backend.adminai.dto.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiAdminToolPlan {

    @JsonProperty("intent")
    private String intent;

    @JsonProperty("tools")
    private List<String> tools;

    @JsonProperty("target_user_id")
    private Long targetUserId;

    @JsonProperty("target_user_query")
    private String targetUserQuery;

    @JsonProperty("skill_keyword")
    private String skillKeyword;

    @JsonProperty("experience_level")
    private String experienceLevel;

    @JsonProperty("fitness_goal")
    private String fitnessGoal;

    @JsonProperty("activity_status")
    private String activityStatus;

    @JsonProperty("inactive_days_threshold")
    private Integer inactiveDaysThreshold;

    @JsonProperty("pr_progression_status")
    private String prProgressionStatus;

    @JsonProperty("coach_relationship_status")
    private String coachRelationshipStatus;

    @JsonProperty("min_completion_rate")
    private Double minCompletionRate;

    @JsonProperty("max_completion_rate")
    private Double maxCompletionRate;

    @JsonProperty("range")
    private String range;

    @JsonProperty("start_date")
    private String startDate;

    @JsonProperty("end_date")
    private String endDate;

    @JsonProperty("include_email")
    private Boolean includeEmail;

    @JsonProperty("program_id")
    private Long programId;

    @JsonProperty("program_title")
    private String programTitle;

    @JsonProperty("program_instruction")
    private String programInstruction;

    @JsonProperty("target_audience")
    private String targetAudience;

    @JsonProperty("target_user_identifier")
    private String targetUserIdentifier;

    @JsonProperty("pro_action_reason")
    private String proActionReason;

    @JsonProperty("exercise_category")
    private String exerciseCategory;

    @JsonProperty("training_type")
    private String trainingType;

    @JsonProperty("bulk_categories")
    private List<String> bulkCategories;

    @JsonProperty("bulk_focus")
    private String bulkFocus;

    @JsonProperty("requested_count")
    private Integer requestedCount;

    @JsonProperty("exercise_drafts")
    private List<ExerciseDraftCandidateDto> exerciseDrafts;
}
