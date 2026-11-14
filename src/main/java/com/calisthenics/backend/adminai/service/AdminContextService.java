package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.AdminPlatformOverviewResponse;
import com.calisthenics.backend.adminai.dto.AdminTrainingAnalyticsResponse;
import com.calisthenics.backend.adminai.dto.ExerciseRecommendationPayload;
import com.calisthenics.backend.adminai.dto.OperationalRecommendationPayload;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.adminai.type.AdminRecommendationPriority;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminContextService {

    private final PlatformAnalyticsService platformAnalyticsService;
    private final TrainingAnalyticsService trainingAnalyticsService;
    private final ExerciseRecommendationService exerciseRecommendationService;

    public record OperationalInsightCandidate(
            AdminRecommendationPriority priority,
            String title,
            String summary,
            String rationale,
            OperationalRecommendationPayload payload
    ) {}

    public record AdminFullContext(
            AdminPlatformOverviewResponse overview,
            AdminTrainingAnalyticsResponse trainingAnalytics,
            List<ExerciseRecommendationPayload> missingExercises,
            List<OperationalInsightCandidate> operationalInsights
    ) {}

    @Transactional(readOnly = true)
    public AdminFullContext buildContext(
            AdminAnalyticsRange range,
            LocalDate startDate,
            LocalDate endDate,
            String targetSkillFilter
    ) {
        AdminPlatformOverviewResponse overview = platformAnalyticsService.getOverview(range, startDate, endDate);
        AdminTrainingAnalyticsResponse training = trainingAnalyticsService.getTrainingAnalytics(range, startDate, endDate);
        List<ExerciseRecommendationPayload> missingExercises =
                exerciseRecommendationService.analyzeMissingExercises(targetSkillFilter, null);
        List<OperationalInsightCandidate> operationalInsights =
                generateOperationalInsights(overview, training);

        return new AdminFullContext(overview, training, missingExercises, operationalInsights);
    }

    public List<OperationalInsightCandidate> generateOperationalInsights(
            AdminPlatformOverviewResponse overview,
            AdminTrainingAnalyticsResponse training
    ) {
        List<OperationalInsightCandidate> insights = new ArrayList<>();

        // 1. User Inactivity Analysis
        AdminTrainingAnalyticsResponse.InactiveUsersSummaryDto inactive = training.getInactiveUsersSummary();
        if (inactive != null && inactive.getTotalUsersEvaluated() > 0) {
            AdminRecommendationPriority priority = inactive.getInactiveRatePercent() >= 50.0
                    ? AdminRecommendationPriority.HIGH
                    : (inactive.getInactiveOver14DaysCount() > 0 ? AdminRecommendationPriority.MEDIUM : AdminRecommendationPriority.LOW);

            insights.add(new OperationalInsightCandidate(
                    priority,
                    "User Retention & Inactivity Follow-up",
                    String.format("%d of %d non-admin users (%.1f%%) have been inactive for 14+ days.",
                            inactive.getInactiveOver14DaysCount(),
                            inactive.getTotalUsersEvaluated(),
                            inactive.getInactiveRatePercent()),
                    "Sustained inactivity directly impacts platform engagement and progression continuity.",
                    OperationalRecommendationPayload.builder()
                            .identifiedIssue("User inactivity across registered athlete accounts")
                            .supportingData(String.format(
                                    "Total non-admin users: %d | Inactive 14+ days: %d | Inactive 30+ days: %d | Never completed a workout: %d (Inactivity rate: %.1f%%)",
                                    inactive.getTotalUsersEvaluated(),
                                    inactive.getInactiveOver14DaysCount(),
                                    inactive.getInactiveOver30DaysCount(),
                                    inactive.getNeverCompletedWorkoutCount(),
                                    inactive.getInactiveRatePercent()))
                            .suggestedAction("Introduce foundational 3-day full-body starter programs and encourage coach onboarding for users with 0 completed workouts.")
                            .expectedBenefit("Higher first-workout activation rate and improved 30-day athlete retention.")
                            .affectedArea("User Engagement & Retention")
                            .build()
            ));
        }

        // 2. Skill Progression Coverage Analysis
        List<AdminTrainingAnalyticsResponse.SkillFocusStatDto> skillsWithGaps = training.getSkillFocusBreakdown() != null
                ? training.getSkillFocusBreakdown().stream()
                        .filter(s -> s.getMissingProgressions() != null && !s.getMissingProgressions().isEmpty())
                        .toList()
                : List.of();
        if (!skillsWithGaps.isEmpty()) {
            List<String> zeroCoverageSkills = skillsWithGaps.stream()
                    .filter(s -> s.getExistingExerciseCount() == 0)
                    .map(AdminTrainingAnalyticsResponse.SkillFocusStatDto::getSkillName)
                    .toList();
            AdminRecommendationPriority priority = !zeroCoverageSkills.isEmpty()
                    ? AdminRecommendationPriority.HIGH
                    : AdminRecommendationPriority.MEDIUM;

            String gapDetails = skillsWithGaps.stream()
                    .map(s -> s.getSkillName() + " (existing: " + s.getExistingExerciseCount()
                            + ", missing: " + s.getMissingProgressions().size() + ")")
                    .collect(Collectors.joining("; "));

            insights.add(new OperationalInsightCandidate(
                    priority,
                    "Skill Progression Library Gaps",
                    "Key calisthenics skill trees have missing intermediate or foundational progressions.",
                    "Athletes plateau or risk injury when progression jumps (e.g., straight to advanced holds) lack intermediate steps.",
                    OperationalRecommendationPayload.builder()
                            .identifiedIssue("Incomplete exercise progression ladders across core calisthenics skills")
                            .supportingData("Skill coverage audit: " + gapDetails)
                            .suggestedAction("Review and approve recommended intermediate progressions (such as Planche Lean, Pseudo Planche Push-Ups, Tuck Front Lever, German Hang, and Human Flag progressions).")
                            .expectedBenefit("Complete beginner-to-elite skill pathways and better AI workout planner variety.")
                            .affectedArea("Exercise Library & Curriculum")
                            .build()
            ));
        }

        // 3. Exercise Category Balance Analysis
        Map<String, Long> byCategory = training.getExerciseLibraryByCategory();
        if (byCategory != null && !byCategory.isEmpty()) {
            long minCatCount = byCategory.values().stream().mapToLong(Long::longValue).min().orElse(0L);
            long maxCatCount = byCategory.values().stream().mapToLong(Long::longValue).max().orElse(0L);
            if (maxCatCount - minCatCount >= 2L || minCatCount <= 1L) {
                String catBreakdown = byCategory.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining(", "));
                insights.add(new OperationalInsightCandidate(
                        minCatCount == 0L ? AdminRecommendationPriority.HIGH : AdminRecommendationPriority.MEDIUM,
                        "Exercise Category Distribution Balance",
                        "Certain movement categories are underrepresented in the active exercise catalog.",
                        "Balanced push, pull, core, legs, skill, and isometric coverage ensures well-rounded weekly workout splits.",
                        OperationalRecommendationPayload.builder()
                                .identifiedIssue("Unbalanced exercise distribution across training categories")
                                .supportingData("Current category counts: " + catBreakdown)
                                .suggestedAction("Add underrepresented CORE, LEGS, and ISOMETRIC exercises to balance the catalog.")
                                .expectedBenefit("More diverse full-body and upper/lower workout generation.")
                                .affectedArea("Exercise Catalog Balance")
                                .build()
                ));
            }
        }

        // 4. Workout Plan Adherence & Coach Utilization
        AdminTrainingAnalyticsResponse.PlanAdherenceSummaryDto adherence = training.getPlanAdherenceSummary();
        if (adherence != null) {
            AdminRecommendationPriority priority = (overview.getPendingCoachClientRelationships() > 0 || adherence.getAdherenceRatePercent() < 60.0)
                    ? AdminRecommendationPriority.MEDIUM
                    : AdminRecommendationPriority.LOW;

            insights.add(new OperationalInsightCandidate(
                    priority,
                    "Workout Plan Adherence & Coaching Operations",
                    String.format("Plan session adherence is %.1f%% with %d active coach-client relationships.",
                            adherence.getAdherenceRatePercent(),
                            overview.getActiveCoachClientRelationships()),
                    "Monitoring assigned plan completion helps identify overly demanding templates or unaddressed coaching requests.",
                    OperationalRecommendationPayload.builder()
                            .identifiedIssue("Workout plan completion and coach-client relationship utilization")
                            .supportingData(String.format(
                                    "Users with active personal plan: %d | Active coach assignments: %d | Pending coach requests: %d | Plan session adherence: %d/%d (%.1f%%)",
                                    adherence.getUsersWithActivePersonalPlan(),
                                    adherence.getActiveCoachAssignmentsCount(),
                                    overview.getPendingCoachClientRelationships(),
                                    adherence.getCompletedAssignedSessions(),
                                    adherence.getTotalAssignedSessions(),
                                    adherence.getAdherenceRatePercent()))
                            .suggestedAction("Review pending coach-client requests and introduce accessible 3-to-4 day draft workout programs.")
                            .expectedBenefit("Higher weekly session completion consistency and stronger coach-client engagement.")
                            .affectedArea("Coaching & Program Operations")
                            .build()
            ));
        }

        return insights;
    }

    public String buildAdminSystemPrompt(AdminFullContext context) {
        AdminPlatformOverviewResponse ov = context.overview();
        AdminTrainingAnalyticsResponse tr = context.trainingAnalytics();

        StringBuilder sb = new StringBuilder();
        sb.append("You are the dedicated Calisthenics AI Admin Agent — an intelligent operations, analytics, and training-program advisor built exclusively for platform administrators.\n");
        sb.append("CRITICAL RULES:\n");
        sb.append("1. NEVER fabricate metrics. Every number you cite MUST come strictly from the Live Platform Context below.\n");
        sb.append("2. NEVER automatically publish exercises or assign workout programs to users. All exercise and workout program recommendations require explicit administrator approval.\n");
        sb.append("3. For Getting Started – 3 Days onboarding campaigns: drafts/unpublished edits execute via tools; publishing or unpublishing ALWAYS requires explicit Admin confirmation; NEVER auto-enroll athletes and NEVER modify existing coach-assigned workout plans.\n");
        sb.append("4. Avoid suggesting exercises that already exist in the catalog unless proposing a clearly distinct progression.\n");
        sb.append("5. Format your 'reply' in clear, executive-ready Markdown with sections, bullet points, and actionable takeaways.\n\n");

        sb.append("=== LIVE PLATFORM ANALYTICS (").append(ov.getRange()).append(": ")
                .append(ov.getStartDate()).append(" to ").append(ov.getEndDate()).append(") ===\n");
        sb.append("- Total Registered Users: ").append(ov.getTotalRegisteredUsers()).append("\n");
        sb.append("- Active Users in Period: ").append(ov.getActiveUsersInPeriod())
                .append(" (").append(ov.getEngagementMetrics().getActiveUserRatePercent()).append("%)\n");
        sb.append("- New Registrations in Period: ").append(ov.getNewRegistrationsInPeriod()).append("\n");
        sb.append("- Total Coaches: ").append(ov.getTotalCoaches())
                .append(" | Total Clients: ").append(ov.getTotalClients())
                .append(" | Total Admins: ").append(ov.getTotalAdmins()).append("\n");
        sb.append("- Active Coach-Client Relationships: ").append(ov.getActiveCoachClientRelationships())
                .append(" | Pending Relationships: ").append(ov.getPendingCoachClientRelationships()).append("\n");
        sb.append("- Total Workout Plans: ").append(ov.getTotalWorkoutPlans())
                .append(" (Active: ").append(ov.getActiveWorkoutPlans())
                .append(", AI-Generated: ").append(ov.getAiGeneratedWorkoutPlans()).append(")\n");
        sb.append("- Completed Workouts (All-Time): ").append(ov.getTotalCompletedWorkoutsAllTime())
                .append(" | Completed in Period: ").append(ov.getCompletedWorkoutsInPeriod()).append("\n");
        sb.append("- Total Recorded PRs (All-Time): ").append(ov.getTotalRecordedPrsAllTime())
                .append(" | Recorded PRs in Period: ").append(ov.getRecordedPrsInPeriod()).append("\n");
        sb.append("- Exercise Catalog: ").append(ov.getTotalExercises())
                .append(" total (").append(ov.getActiveExercises()).append(" active)\n");
        sb.append("- Workout Completion Rate: ").append(ov.getEngagementMetrics().getWorkoutCompletionRatePercent()).append("%\n\n");

        sb.append("=== EXERCISE LIBRARY DISTRIBUTION ===\n");
        sb.append("- By Category: ").append(tr.getExerciseLibraryByCategory()).append("\n");
        sb.append("- By Difficulty: ").append(tr.getExerciseLibraryByDifficulty()).append("\n");

        if (tr.getMostPerformedExercises() != null && !tr.getMostPerformedExercises().isEmpty()) {
            String topEx = tr.getMostPerformedExercises().stream().limit(5)
                    .map(e -> e.getExerciseName() + " (sessions=" + e.getSessionPerformances()
                            + ", plans=" + e.getPlanAssignmentsCount()
                            + ", completion=" + e.getCompletionRatePercent() + "%)")
                    .collect(Collectors.joining("; "));
            sb.append("- Top Exercises: ").append(topEx).append("\n");
        }

        if (tr.getSkillFocusBreakdown() != null && !tr.getSkillFocusBreakdown().isEmpty()) {
            sb.append("\n=== SKILL COVERAGE & MISSING PROGRESSIONS ===\n");
            for (AdminTrainingAnalyticsResponse.SkillFocusStatDto s : tr.getSkillFocusBreakdown()) {
                sb.append("- ").append(s.getSkillName())
                        .append(": existingExercises=").append(s.getExistingExerciseCount())
                        .append(", planRefs=").append(s.getPlanReferencesCount())
                        .append(", sessionPerfs=").append(s.getSessionPerformancesCount())
                        .append(", missingProgressions=").append(s.getMissingProgressions())
                        .append("\n");
            }
        }

        return sb.toString();
    }
}
