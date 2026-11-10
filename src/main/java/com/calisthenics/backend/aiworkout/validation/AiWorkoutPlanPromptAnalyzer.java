package com.calisthenics.backend.aiworkout.validation;

import com.calisthenics.backend.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class AiWorkoutPlanPromptAnalyzer {

    // Match explicit training frequency like "3-day/week", "5 days per week", "4-day split", "3 buổi/tuần".
    // Duration tokens such as "4-week" or "4 weeks" are intentionally excluded.
    private static final Pattern FREQUENCY_PER_WEEK_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*[-\\s]?(?:training\\s+)?(?:days?|buoi|buổi|ngay|ngày)\\s*(?:/|\\s*(?:per|a)\\s*)(?:week|wk|tuan|tuần)\\b"
    );
    private static final Pattern SPLIT_DAY_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*[-\\s]?(?:day|days|ngay|ngày)\\s+(?:calisthenics|workout|training|split|plan|routine|program|schedule)\\b"
    );
    private static final Pattern STANDALONE_DAY_PLAN_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*[-\\s]?(?:day|days)\\b(?!\\s*(?:streak|rest|ago|after|later|week|weeks))"
    );
    private static final Pattern DURATION_WEEKS_PATTERN = Pattern.compile(
            "(?i)\\b(\\d{1,2})\\s*[-\\s]?(?:week|weeks|tuan|tuần)\\b"
    );

    private final AppProperties appProperties;

    public record PromptConstraints(
            OptionalInt requestedTrainingDaysPerWeek,
            int targetDurationWeeks,
            String fitnessLevel,
            int maxExercisesPerSession,
            int minExercisesPerSession,
            int recommendedMinExercises,
            int recommendedMaxExercises
    ) {}

    public PromptConstraints analyze(String prompt, String fitnessLevel, Integer explicitDurationWeeks) {
        AppProperties.WorkoutPlanner cfg = appProperties.getWorkoutPlanner();

        OptionalInt requestedDays = extractTrainingDaysPerWeek(prompt);
        if (requestedDays.isPresent()) {
            int days = requestedDays.getAsInt();
            if (days < cfg.getMinTrainingDaysPerWeek() || days > cfg.getMaxTrainingDaysPerWeek()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Weekly training frequency must be between "
                                + cfg.getMinTrainingDaysPerWeek() + " and "
                                + cfg.getMaxTrainingDaysPerWeek() + " days per week."
                );
            }
        }

        int durationWeeks = resolveDurationWeeks(prompt, explicitDurationWeeks, cfg);
        String normalizedLevel = normalizeFitnessLevel(fitnessLevel);
        int hardMax = Math.min(10, Math.max(1, cfg.getMaxExercisesPerSession()));
        int levelMax = resolveLevelMaxExercises(normalizedLevel, hardMax, cfg);

        return new PromptConstraints(
                requestedDays,
                durationWeeks,
                normalizedLevel,
                levelMax,
                Math.max(1, cfg.getMinExercisesPerSession()),
                Math.max(1, Math.min(cfg.getRecommendedMinExercises(), levelMax)),
                Math.max(1, Math.min(cfg.getRecommendedMaxExercises(), levelMax))
        );
    }

    public int maxExercisesForSession(PromptConstraints constraints, String sessionTitle) {
        AppProperties.WorkoutPlanner cfg = appProperties.getWorkoutPlanner();
        int hardCap = Math.min(10, constraints.maxExercisesPerSession());
        if (sessionTitle == null) {
            return hardCap;
        }
        String lower = sessionTitle.toLowerCase(Locale.US);
        if (lower.contains("mobility") || lower.contains("recovery") || lower.contains("stretch")) {
            return Math.min(hardCap, Math.max(4, cfg.getSkillMobilityMaxExercises()));
        }
        if (lower.contains("handstand") && !lower.contains("push")) {
            return Math.min(hardCap, Math.max(4, cfg.getSkillMobilityMaxExercises()));
        }
        return hardCap;
    }

    OptionalInt extractTrainingDaysPerWeek(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return OptionalInt.empty();
        }
        Matcher m1 = FREQUENCY_PER_WEEK_PATTERN.matcher(prompt);
        if (m1.find()) {
            return parsePositiveInt(m1.group(1));
        }
        Matcher m2 = SPLIT_DAY_PATTERN.matcher(prompt);
        if (m2.find()) {
            return parsePositiveInt(m2.group(1));
        }
        Matcher m3 = STANDALONE_DAY_PLAN_PATTERN.matcher(prompt);
        if (m3.find()) {
            return parsePositiveInt(m3.group(1));
        }
        return OptionalInt.empty();
    }

    private int resolveDurationWeeks(String prompt, Integer explicitDurationWeeks, AppProperties.WorkoutPlanner cfg) {
        int weeks = explicitDurationWeeks != null ? explicitDurationWeeks : 0;
        if (weeks <= 0 && prompt != null) {
            Matcher m = DURATION_WEEKS_PATTERN.matcher(prompt);
            if (m.find()) {
                OptionalInt parsed = parsePositiveInt(m.group(1));
                if (parsed.isPresent()) {
                    weeks = parsed.getAsInt();
                }
            }
        }
        if (weeks <= 0) {
            return 4;
        }
        if (weeks < cfg.getMinDurationWeeks() || weeks > cfg.getMaxDurationWeeks()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Program duration must be between "
                            + cfg.getMinDurationWeeks() + " and "
                            + cfg.getMaxDurationWeeks() + " weeks."
            );
        }
        return weeks;
    }

    private String normalizeFitnessLevel(String fitnessLevel) {
        if (fitnessLevel == null || fitnessLevel.isBlank()) {
            return "INTERMEDIATE";
        }
        return fitnessLevel.trim().toUpperCase(Locale.US);
    }

    private int resolveLevelMaxExercises(String level, int hardMax, AppProperties.WorkoutPlanner cfg) {
        return switch (level) {
            case "BEGINNER" -> Math.min(hardMax, Math.max(4, cfg.getBeginnerMaxExercises()));
            case "ADVANCED", "ELITE" -> Math.min(hardMax, Math.max(4, cfg.getAdvancedMaxExercises()));
            default -> Math.min(hardMax, Math.max(4, cfg.getIntermediateMaxExercises()));
        };
    }

    private OptionalInt parsePositiveInt(String raw) {
        try {
            return OptionalInt.of(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException ex) {
            return OptionalInt.empty();
        }
    }
}
