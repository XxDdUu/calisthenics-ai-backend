package com.calisthenics.backend.analytics.service;

import com.calisthenics.backend.analytics.dto.*;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.muscle.MuscleId;
import com.calisthenics.backend.exercise.muscle.MuscleRegion;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TrainingAnalyticsService {

    private final WorkoutSessionRepository workoutSessionRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final ExerciseRepository exerciseRepository;
    private final CoachClientRelationshipRepository coachClientRelationshipRepository;
    private final PersonalRecordService personalRecordService;
    private final UserTimeResolver userTimeResolver;
    private final MuscleTaxonomyService muscleTaxonomyService;

    public TrainingAnalyticsService(
            WorkoutSessionRepository workoutSessionRepository,
            PersonalRecordRepository personalRecordRepository,
            ExerciseRepository exerciseRepository,
            CoachClientRelationshipRepository coachClientRelationshipRepository,
            PersonalRecordService personalRecordService
    ) {
        this(workoutSessionRepository, personalRecordRepository, exerciseRepository,
                coachClientRelationshipRepository, personalRecordService,
                new UserTimeResolver(new AppProperties()), new MuscleTaxonomyService());
    }

    public TrainingAnalyticsService(
            WorkoutSessionRepository workoutSessionRepository,
            PersonalRecordRepository personalRecordRepository,
            ExerciseRepository exerciseRepository,
            CoachClientRelationshipRepository coachClientRelationshipRepository,
            PersonalRecordService personalRecordService,
            UserTimeResolver userTimeResolver
    ) {
        this(workoutSessionRepository, personalRecordRepository, exerciseRepository,
                coachClientRelationshipRepository, personalRecordService,
                userTimeResolver, new MuscleTaxonomyService());
    }

    @Autowired
    public TrainingAnalyticsService(
            WorkoutSessionRepository workoutSessionRepository,
            PersonalRecordRepository personalRecordRepository,
            ExerciseRepository exerciseRepository,
            CoachClientRelationshipRepository coachClientRelationshipRepository,
            PersonalRecordService personalRecordService,
            UserTimeResolver userTimeResolver,
            MuscleTaxonomyService muscleTaxonomyService
    ) {
        this.workoutSessionRepository = workoutSessionRepository;
        this.personalRecordRepository = personalRecordRepository;
        this.exerciseRepository = exerciseRepository;
        this.coachClientRelationshipRepository = coachClientRelationshipRepository;
        this.personalRecordService = personalRecordService;
        this.userTimeResolver = userTimeResolver;
        this.muscleTaxonomyService = muscleTaxonomyService != null ? muscleTaxonomyService : new MuscleTaxonomyService();
    }

    @Transactional(readOnly = true)
    public ExerciseProgressResponse getExerciseProgress(Long userId, Long exerciseId, String filter) {
        return getExerciseProgress(userId, exerciseId, filter, null);
    }

    @Transactional(readOnly = true)
    public ExerciseProgressResponse getExerciseProgress(Long userId, Long exerciseId, String filter, String timezone) {
        Exercise exercise = exerciseRepository.findById(exerciseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found"));

        List<WorkoutSession> allSessions = workoutSessionRepository.findCompletedSessionsByExercise(
                userId, WorkoutSessionStatus.COMPLETED, exerciseId);

        LocalDate today = userTimeResolver.today(timezone);
        LocalDate filterStartDate = resolveFilterStartDate(filter, today);
        List<WorkoutSession> filteredSessions = allSessions.stream()
                .filter(s -> filterStartDate == null || (s.getWorkoutDate() != null && !s.getWorkoutDate().isBefore(filterStartDate)))
                .toList();

        List<PersonalRecord> exercisePRs = personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedAtDesc(
                userId, exerciseId);
        Set<Long> prSessionIds = exercisePRs.stream()
                .map(pr -> pr.getWorkoutSession().getId())
                .collect(Collectors.toSet());

        Map<Long, List<String>> sessionPrTypes = new HashMap<>();
        for (PersonalRecord pr : exercisePRs) {
            sessionPrTypes.computeIfAbsent(pr.getWorkoutSession().getId(), k -> new ArrayList<>())
                    .add(pr.getRecordType().name());
        }

        int totalCompletedSessions = allSessions.size();
        int totalSets = 0;
        int totalReps = 0;
        int totalHoldDuration = 0;
        LocalDate lastPerformedDate = allSessions.isEmpty() ? null : allSessions.getFirst().getWorkoutDate();

        List<ExerciseHistorySessionDto> historySessions = new ArrayList<>();
        List<ProgressChartDataPointDto> chartDataPoints = new ArrayList<>();

        for (WorkoutSession session : filteredSessions) {
            WorkoutSessionExercise exItem = session.getExercises().stream()
                    .filter(e -> e.getExercise() != null && e.getExercise().getId().equals(exerciseId))
                    .findFirst()
                    .orElse(null);

            if (exItem == null) continue;

            List<WorkoutSessionSet> completedSets = exItem.getSets().stream()
                    .filter(WorkoutSessionSet::isCompleted)
                    .toList();

            List<ExerciseHistorySetDto> setDtos = new ArrayList<>();
            BigDecimal sessionBestWeight = null;
            Integer sessionBestReps = null;
            Integer sessionBestHold = null;
            BigDecimal sessionVolume = BigDecimal.ZERO;
            boolean hasWeight = false;

            for (WorkoutSessionSet s : completedSets) {
                totalSets++;
                if (s.getReps() != null) totalReps += s.getReps();
                if (s.getHoldDurationSeconds() != null) totalHoldDuration += s.getHoldDurationSeconds();

                setDtos.add(ExerciseHistorySetDto.builder()
                        .setNumber(s.getSetNumber())
                        .reps(s.getReps())
                        .holdDurationSeconds(s.getHoldDurationSeconds())
                        .externalWeight(s.getWeightKg())
                        .completed(s.isCompleted())
                        .build());

                if (s.getWeightKg() != null && s.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
                    hasWeight = true;
                    if (sessionBestWeight == null || s.getWeightKg().compareTo(sessionBestWeight) > 0) {
                        sessionBestWeight = s.getWeightKg();
                    }
                    if (s.getReps() != null) {
                        sessionVolume = sessionVolume.add(s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())));
                    }
                }
                if (s.getReps() != null) {
                    if (sessionBestReps == null || s.getReps() > sessionBestReps) {
                        sessionBestReps = s.getReps();
                    }
                }
                if (s.getHoldDurationSeconds() != null) {
                    if (sessionBestHold == null || s.getHoldDurationSeconds() > sessionBestHold) {
                        sessionBestHold = s.getHoldDurationSeconds();
                    }
                }
            }

            boolean isPr = prSessionIds.contains(session.getId());
            List<String> prTypes = sessionPrTypes.getOrDefault(session.getId(), Collections.emptyList());

            historySessions.add(ExerciseHistorySessionDto.builder()
                    .sessionId(session.getId())
                    .date(session.getWorkoutDate())
                    .sets(setDtos)
                    .bestWeight(sessionBestWeight)
                    .bestReps(sessionBestReps)
                    .bestHoldDuration(sessionBestHold)
                    .totalVolume(sessionVolume.setScale(2, RoundingMode.HALF_UP))
                    .isPersonalRecord(isPr)
                    .prTypes(prTypes)
                    .build());

            // Build chart point
            BigDecimal chartPrimaryValue;
            if (exercise.getCategory() == ExerciseCategory.ISOMETRIC) {
                chartPrimaryValue = sessionBestHold != null ? BigDecimal.valueOf(sessionBestHold) : BigDecimal.ZERO;
            } else if (hasWeight && sessionBestWeight != null) {
                chartPrimaryValue = sessionBestWeight;
            } else {
                chartPrimaryValue = sessionBestReps != null ? BigDecimal.valueOf(sessionBestReps) : BigDecimal.ZERO;
            }

            chartDataPoints.add(ProgressChartDataPointDto.builder()
                    .date(session.getWorkoutDate())
                    .value(chartPrimaryValue)
                    .secondaryValue(sessionBestReps != null ? BigDecimal.valueOf(sessionBestReps) : null)
                    .volume(sessionVolume.setScale(2, RoundingMode.HALF_UP))
                    .reps(sessionBestReps)
                    .holdDurationSeconds(sessionBestHold)
                    .isPersonalRecord(isPr)
                    .build());
        }

        // Chart data points in ascending chronological order for charts
        Collections.reverse(chartDataPoints);

        // Progressive Overload Comparison: latest vs previous completed session
        ProgressiveOverloadComparisonDto comparison = calculateOverloadComparison(allSessions, exerciseId, exercise.getCategory());

        // Format best set overall
        String bestSetFormatted = resolveBestSetFormatted(allSessions, exerciseId, exercise.getCategory());

        List<PersonalRecordResponse> prResponses = exercisePRs.stream()
                .map(personalRecordService::mapToResponse)
                .toList();

        MuscleTaxonomyService.ResolvedMuscleTargets resolvedMuscles = muscleTaxonomyService.resolveTargets(exercise);

        return ExerciseProgressResponse.builder()
                .exerciseId(exercise.getId())
                .exerciseName(exercise.getName())
                .category(exercise.getCategory().name())
                .difficulty(exercise.getDifficulty() != null ? exercise.getDifficulty().name() : null)
                .description(exercise.getDescription())
                .instructions(exercise.getInstructions())
                .targetMuscles(exercise.getTargetMuscles())
                .secondaryMuscles(exercise.getSecondaryMuscles())
                .primaryMuscles(resolvedMuscles.primaryMuscleIds())
                .primaryMuscleIds(resolvedMuscles.primaryMuscleIds())
                .secondaryMuscleIds(resolvedMuscles.secondaryMuscleIds())
                .muscleTargets(resolvedMuscles.muscleTargets())
                .totalCompletedSessions(totalCompletedSessions)
                .totalSets(totalSets)
                .totalReps(totalReps)
                .totalHoldDurationSeconds(totalHoldDuration)
                .bestSet(bestSetFormatted)
                .lastPerformedDate(lastPerformedDate)
                .personalRecords(prResponses)
                .latestVsPrevious(comparison)
                .chartData(chartDataPoints)
                .sessions(historySessions)
                .build();
    }

    private ProgressiveOverloadComparisonDto calculateOverloadComparison(
            List<WorkoutSession> allSessions, Long exerciseId, ExerciseCategory category) {

        if (allSessions.size() < 2) {
            return ProgressiveOverloadComparisonDto.builder()
                    .hasComparison(false)
                    .summaryText("Complete at least 2 sessions to compare progressive overload.")
                    .build();
        }

        WorkoutSession latestSession = allSessions.get(0);
        WorkoutSession prevSession = allSessions.get(1);

        WorkoutSessionExercise latestEx = findExerciseInSession(latestSession, exerciseId);
        WorkoutSessionExercise prevEx = findExerciseInSession(prevSession, exerciseId);

        if (latestEx == null || prevEx == null) {
            return ProgressiveOverloadComparisonDto.builder()
                    .hasComparison(false)
                    .summaryText("Insufficient session data for progressive overload comparison.")
                    .build();
        }

        // Best metrics
        BigDecimal latestWeight = findBestWeight(latestEx);
        BigDecimal prevWeight = findBestWeight(prevEx);

        Integer latestReps = findBestReps(latestEx);
        Integer prevReps = findBestReps(prevEx);

        Integer latestHold = findBestHold(latestEx);
        Integer prevHold = findBestHold(prevEx);

        BigDecimal latestVol = calculateExerciseVolume(latestEx);
        BigDecimal prevVol = calculateExerciseVolume(prevEx);

        BigDecimal weightDiff = (latestWeight != null && prevWeight != null) ? latestWeight.subtract(prevWeight) : null;
        BigDecimal weightPct = calculatePercentage(latestWeight, prevWeight);

        Integer repsDiff = (latestReps != null && prevReps != null) ? latestReps - prevReps : null;
        BigDecimal repsPct = (latestReps != null && prevReps != null && prevReps > 0)
                ? BigDecimal.valueOf(latestReps - prevReps)
                .divide(BigDecimal.valueOf(prevReps), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;

        Integer durationDiff = (latestHold != null && prevHold != null) ? latestHold - prevHold : null;
        BigDecimal durationPct = (latestHold != null && prevHold != null && prevHold > 0)
                ? BigDecimal.valueOf(latestHold - prevHold)
                .divide(BigDecimal.valueOf(prevHold), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP)
                : null;

        BigDecimal volumeDiff = latestVol.subtract(prevVol);
        BigDecimal volumePct = calculatePercentage(latestVol, prevVol);

        // Generate summary text
        StringBuilder summary = new StringBuilder();
        if (weightDiff != null && weightDiff.compareTo(BigDecimal.ZERO) > 0) {
            summary.append("+").append(weightDiff.stripTrailingZeros().toPlainString()).append(" kg external load");
            if (weightPct != null) summary.append(" (+").append(weightPct).append("%)");
            summary.append(" compared to previous session.");
        } else if (durationDiff != null && durationDiff > 0) {
            summary.append("+").append(durationDiff).append("s hold duration");
            if (durationPct != null) summary.append(" (+").append(durationPct).append("%)");
            summary.append(" compared to previous session.");
        } else if (repsDiff != null && repsDiff > 0) {
            summary.append("+").append(repsDiff).append(" reps");
            if (repsPct != null) summary.append(" (+").append(repsPct).append("%)");
            summary.append(" compared to previous session.");
        } else if (volumeDiff.compareTo(BigDecimal.ZERO) > 0) {
            summary.append("+").append(volumeDiff.stripTrailingZeros().toPlainString()).append(" kg total volume");
            if (volumePct != null) summary.append(" (+").append(volumePct).append("%)");
            summary.append(" compared to previous session.");
        } else {
            summary.append("Performance maintained compared to previous session.");
        }

        return ProgressiveOverloadComparisonDto.builder()
                .hasComparison(true)
                .latestDate(latestSession.getWorkoutDate())
                .previousDate(prevSession.getWorkoutDate())
                .weightDiff(weightDiff)
                .weightPercentage(weightPct)
                .repsDiff(repsDiff)
                .repsPercentage(repsPct)
                .durationDiff(durationDiff)
                .durationPercentage(durationPct)
                .volumeDiff(volumeDiff)
                .volumePercentage(volumePct)
                .summaryText(summary.toString())
                .build();
    }

    private String resolveBestSetFormatted(List<WorkoutSession> sessions, Long exerciseId, ExerciseCategory category) {
        BigDecimal bestWeight = null;
        Integer bestWeightReps = null;
        Integer bestReps = null;
        Integer bestHold = null;

        for (WorkoutSession s : sessions) {
            WorkoutSessionExercise ex = findExerciseInSession(s, exerciseId);
            if (ex == null) continue;
            for (WorkoutSessionSet set : ex.getSets()) {
                if (!set.isCompleted()) continue;
                if (set.getWeightKg() != null && set.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
                    if (bestWeight == null || set.getWeightKg().compareTo(bestWeight) > 0) {
                        bestWeight = set.getWeightKg();
                        bestWeightReps = set.getReps();
                    }
                }
                if (set.getReps() != null && (bestReps == null || set.getReps() > bestReps)) {
                    bestReps = set.getReps();
                }
                if (set.getHoldDurationSeconds() != null && (bestHold == null || set.getHoldDurationSeconds() > bestHold)) {
                    bestHold = set.getHoldDurationSeconds();
                }
            }
        }

        if (bestWeight != null) {
            return "+" + bestWeight.stripTrailingZeros().toPlainString() + " kg" +
                    (bestWeightReps != null ? " × " + bestWeightReps + " reps" : "");
        } else if (category == ExerciseCategory.ISOMETRIC && bestHold != null) {
            return bestHold + "s hold";
        } else if (bestReps != null) {
            return bestReps + " reps";
        }
        return "No completed sets recorded";
    }

    @Transactional(readOnly = true)
    public TrainingOverviewResponse getTrainingOverview(Long userId) {
        return getTrainingOverview(userId, null);
    }

    @Transactional(readOnly = true)
    public TrainingOverviewResponse getTrainingOverview(Long userId, String timezone) {
        LocalDate today = userTimeResolver.today(timezone);
        List<LocalDate> distinctDates = workoutSessionRepository.findDistinctCompletedWorkoutDates(
                userId, WorkoutSessionStatus.COMPLETED);

        int totalWorkouts = (int) workoutSessionRepository.countByUserIdAndStatus(
                userId, WorkoutSessionStatus.COMPLETED);
        int totalActiveDays = distinctDates.size();

        int currentStreak = calculateCurrentStreak(distinctDates, today);
        int longestStreak = calculateLongestStreak(distinctDates);

        // Weekly frequency over last 28 days
        LocalDate fourWeeksAgo = today.minusWeeks(4);
        long recentActiveDays = distinctDates.stream().filter(d -> !d.isBefore(fourWeeksAgo)).count();
        double weeklyFrequency = Math.round((recentActiveDays / 4.0) * 10.0) / 10.0;

        int totalPRs = (int) personalRecordRepository.countByUserId(userId);
        List<PersonalRecordResponse> recentPRs = personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId).stream()
                .limit(5)
                .map(personalRecordService::mapToResponse)
                .toList();

        return TrainingOverviewResponse.builder()
                .totalWorkouts(totalWorkouts)
                .totalActiveDays(totalActiveDays)
                .currentStreak(currentStreak)
                .longestStreak(longestStreak)
                .weeklyFrequency(weeklyFrequency)
                .totalPRs(totalPRs)
                .recentPRs(recentPRs)
                .build();
    }

    @Transactional(readOnly = true)
    public WeeklyAnalyticsResponse getWeeklyAnalytics(Long userId) {
        return getWeeklyAnalytics(userId, null);
    }

    @Transactional(readOnly = true)
    public WeeklyAnalyticsResponse getWeeklyAnalytics(Long userId, String timezone) {
        LocalDate today = userTimeResolver.today(timezone);
        LocalDate currentWeekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate currentWeekEnd = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        LocalDate prevWeekStart = currentWeekStart.minusWeeks(1);
        LocalDate prevWeekEnd = currentWeekStart.minusDays(1);

        List<WorkoutSession> currentWeekSessions = workoutSessionRepository.findCompletedSessionsBetweenDates(
                userId, WorkoutSessionStatus.COMPLETED, currentWeekStart, currentWeekEnd);

        List<WorkoutSession> prevWeekSessions = workoutSessionRepository.findCompletedSessionsBetweenDates(
                userId, WorkoutSessionStatus.COMPLETED, prevWeekStart, prevWeekEnd);

        int currWorkouts = currentWeekSessions.size();
        int prevWorkouts = prevWeekSessions.size();
        BigDecimal workoutsChange = calculatePercentage(BigDecimal.valueOf(currWorkouts), BigDecimal.valueOf(prevWorkouts));

        int currSets = currentWeekSessions.stream().flatMap(s -> s.getExercises().stream())
                .flatMap(e -> e.getSets().stream()).filter(WorkoutSessionSet::isCompleted).mapToInt(s -> 1).sum();
        int prevSets = prevWeekSessions.stream().flatMap(s -> s.getExercises().stream())
                .flatMap(e -> e.getSets().stream()).filter(WorkoutSessionSet::isCompleted).mapToInt(s -> 1).sum();
        BigDecimal setsChange = calculatePercentage(BigDecimal.valueOf(currSets), BigDecimal.valueOf(prevSets));

        int currReps = currentWeekSessions.stream().flatMap(s -> s.getExercises().stream())
                .flatMap(e -> e.getSets().stream()).filter(WorkoutSessionSet::isCompleted)
                .map(WorkoutSessionSet::getReps).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        int prevReps = prevWeekSessions.stream().flatMap(s -> s.getExercises().stream())
                .flatMap(e -> e.getSets().stream()).filter(WorkoutSessionSet::isCompleted)
                .map(WorkoutSessionSet::getReps).filter(Objects::nonNull).mapToInt(Integer::intValue).sum();
        BigDecimal repsChange = calculatePercentage(BigDecimal.valueOf(currReps), BigDecimal.valueOf(prevReps));

        BigDecimal currVolume = calculateSessionsVolume(currentWeekSessions);
        BigDecimal prevVolume = calculateSessionsVolume(prevWeekSessions);
        BigDecimal volumeChange = calculatePercentage(currVolume, prevVolume);

        long currDuration = currentWeekSessions.stream()
                .mapToLong(s -> s.getDurationSeconds() != null ? s.getDurationSeconds() : 0).sum();
        long prevDuration = prevWeekSessions.stream()
                .mapToLong(s -> s.getDurationSeconds() != null ? s.getDurationSeconds() : 0).sum();
        BigDecimal durationChange = calculatePercentage(BigDecimal.valueOf(currDuration), BigDecimal.valueOf(prevDuration));

        int exercisesPerformed = (int) currentWeekSessions.stream()
                .flatMap(s -> s.getExercises().stream())
                .map(WorkoutSessionExercise::getExercise)
                .filter(Objects::nonNull)
                .map(Exercise::getId)
                .distinct()
                .count();

        Map<String, Integer> daily = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = currentWeekStart.plusDays(i);
            int count = (int) currentWeekSessions.stream()
                    .filter(s -> day.equals(s.getWorkoutDate()))
                    .count();
            String dayKey = day.getDayOfWeek().name().substring(0, 1) + day.getDayOfWeek().name().substring(1, 3).toLowerCase();
            daily.put(dayKey, count);
        }

        return WeeklyAnalyticsResponse.builder()
                .startDate(currentWeekStart.toString())
                .endDate(currentWeekEnd.toString())
                .currentWeekWorkouts(currWorkouts)
                .previousWeekWorkouts(prevWorkouts)
                .workoutsChangePercentage(workoutsChange)
                .currentWeekSets(currSets)
                .previousWeekSets(prevSets)
                .setsChangePercentage(setsChange)
                .currentWeekReps(currReps)
                .previousWeekReps(prevReps)
                .repsChangePercentage(repsChange)
                .currentWeekVolume(currVolume.setScale(2, RoundingMode.HALF_UP))
                .previousWeekVolume(prevVolume.setScale(2, RoundingMode.HALF_UP))
                .volumeChangePercentage(volumeChange)
                .currentWeekDurationSeconds(currDuration)
                .previousWeekDurationSeconds(prevDuration)
                .durationChangePercentage(durationChange)
                .exercisesPerformed(exercisesPerformed)
                .dailyWorkouts(daily)
                .muscleMap(buildWeeklyMuscleAnalytics(currentWeekSessions, currentWeekStart, currentWeekEnd))
                .build();
    }

    @Transactional(readOnly = true)
    public WeeklyMuscleAnalyticsResponse getWeeklyMuscleAnalytics(Long userId, int weekOffset, String timezone) {
        LocalDate today = userTimeResolver.today(timezone);
        LocalDate targetDate = today.plusWeeks(weekOffset);
        LocalDate startDate = targetDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endDate = targetDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<WorkoutSession> sessions = workoutSessionRepository.findCompletedSessionsBetweenDates(
                userId, WorkoutSessionStatus.COMPLETED, startDate, endDate);

        return buildWeeklyMuscleAnalytics(sessions, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public WeeklyMuscleAnalyticsResponse getCoachClientWeeklyMuscleAnalytics(
            Long coachId, Long clientId, int weekOffset, String timezone) {
        verifyCoachAccess(coachId, clientId);
        return getWeeklyMuscleAnalytics(clientId, weekOffset, timezone);
    }

    private WeeklyMuscleAnalyticsResponse buildWeeklyMuscleAnalytics(
            List<WorkoutSession> sessions, LocalDate startDate, LocalDate endDate) {
        EnumMap<MuscleId, MuscleVolumeAccumulator> accumulators = new EnumMap<>(MuscleId.class);
        for (MuscleId muscle : MuscleId.orderedValues()) {
            accumulators.put(muscle, new MuscleVolumeAccumulator());
        }

        List<WorkoutSession> completedSessions = sessions == null ? List.of() : sessions.stream()
                .filter(s -> s != null && s.getStatus() == WorkoutSessionStatus.COMPLETED)
                .toList();

        int totalCompletedWorkouts = completedSessions.size();
        int totalCompletedSets = 0;
        int unmappedCompletedSets = 0;
        Map<Exercise, MuscleTaxonomyService.ResolvedMuscleTargets> resolvedCache = new IdentityHashMap<>();

        for (WorkoutSession session : completedSessions) {
            if (session.getExercises() == null) {
                continue;
            }
            for (WorkoutSessionExercise exItem : session.getExercises()) {
                if (exItem == null || exItem.getSets() == null) {
                    continue;
                }
                List<WorkoutSessionSet> completedSets = exItem.getSets().stream()
                        .filter(s -> s != null && s.isCompleted())
                        .toList();
                if (completedSets.isEmpty()) {
                    continue;
                }

                Exercise exercise = exItem.getExercise();
                MuscleTaxonomyService.ResolvedMuscleTargets resolved = exercise == null
                        ? MuscleTaxonomyService.ResolvedMuscleTargets.empty()
                        : resolvedCache.computeIfAbsent(exercise, muscleTaxonomyService::resolveTargets);

                boolean hasTargets = !resolved.primaryMuscleIds().isEmpty() || !resolved.secondaryMuscleIds().isEmpty();

                for (WorkoutSessionSet set : completedSets) {
                    totalCompletedSets++;
                    if (!hasTargets) {
                        unmappedCompletedSets++;
                        continue;
                    }

                    int reps = (set.getReps() != null && set.getReps() > 0) ? set.getReps() : 0;
                    int holdSeconds = (set.getHoldDurationSeconds() != null && set.getHoldDurationSeconds() > 0)
                            ? set.getHoldDurationSeconds()
                            : 0;
                    double volumeKg = (set.getWeightKg() != null
                            && set.getWeightKg().compareTo(BigDecimal.ZERO) > 0
                            && reps > 0)
                            ? set.getWeightKg().doubleValue() * reps
                            : 0.0;

                    for (String primaryId : resolved.primaryMuscleIds()) {
                        MuscleId.fromId(primaryId).ifPresent(muscle ->
                                accumulators.get(muscle).addPrimary(reps, holdSeconds, volumeKg));
                    }
                    for (String secondaryId : resolved.secondaryMuscleIds()) {
                        MuscleId.fromId(secondaryId).ifPresent(muscle ->
                                accumulators.get(muscle).addSecondary(reps, holdSeconds, volumeKg));
                    }
                }
            }
        }

        double totalEffectiveSetsRaw = 0.0;
        EnumMap<MuscleRegion, Double> regionSets = new EnumMap<>(MuscleRegion.class);
        for (MuscleRegion region : MuscleRegion.values()) {
            regionSets.put(region, 0.0);
        }

        for (MuscleId muscle : MuscleId.orderedValues()) {
            double eff = accumulators.get(muscle).effectiveSets();
            totalEffectiveSetsRaw += eff;
            regionSets.put(muscle.getRegion(), regionSets.get(muscle.getRegion()) + eff);
        }
        double totalEffectiveSets = round1(totalEffectiveSetsRaw);

        List<MuscleVolumeDistributionDto> muscleDtos = new ArrayList<>(22);
        List<String> undertrainedMuscles = new ArrayList<>();
        List<String> optimalMuscles = new ArrayList<>();
        List<String> overloadedMuscles = new ArrayList<>();

        for (MuscleId muscle : MuscleId.orderedValues()) {
            MuscleVolumeAccumulator acc = accumulators.get(muscle);
            double effectiveSets = round1(acc.effectiveSets());
            double distributionPct = totalEffectiveSets > 0.0
                    ? round1((effectiveSets / totalEffectiveSets) * 100.0)
                    : 0.0;
            double activationIntensity = effectiveSets <= 0.0
                    ? 0.0
                    : Math.min(1.0, Math.max(0.18, round2(effectiveSets / 16.0)));
            String status = resolveMuscleTrainingStatus(effectiveSets);

            if ("UNDERTRAINED".equals(status)) {
                undertrainedMuscles.add(muscle.getId());
            } else if ("OPTIMAL".equals(status)) {
                optimalMuscles.add(muscle.getId());
            } else if ("OVERLOADED".equals(status)) {
                overloadedMuscles.add(muscle.getId());
            }

            muscleDtos.add(MuscleVolumeDistributionDto.builder()
                    .muscleId(muscle.getId())
                    .displayName(muscle.getDisplayName())
                    .region(muscle.getRegion())
                    .primaryView(muscle.getPrimaryView())
                    .views(muscle.getViews())
                    .svgElementIds(muscle.getSvgElementIds())
                    .displayOrder(muscle.getDisplayOrder())
                    .primarySets(acc.primarySets)
                    .secondarySets(acc.secondarySets)
                    .effectiveSets(effectiveSets)
                    .primaryReps(acc.primaryReps)
                    .secondaryReps(acc.secondaryReps)
                    .primaryHoldSeconds(acc.primaryHoldSeconds)
                    .secondaryHoldSeconds(acc.secondaryHoldSeconds)
                    .primaryVolumeKg(round2(acc.primaryVolumeKg))
                    .secondaryVolumeKg(round2(acc.secondaryVolumeKg))
                    .distributionPercentage(distributionPct)
                    .activationIntensity(activationIntensity)
                    .trainingStatus(status)
                    .build());
        }

        Map<String, Double> regionDistribution = computeRegionDistribution(regionSets, totalEffectiveSets);

        List<String> caveats = new ArrayList<>();
        if (totalCompletedWorkouts == 0) {
            caveats.add("No completed workouts recorded for this week yet.");
        } else if (totalCompletedSets == 0) {
            caveats.add("Completed workouts for this week have no completed sets recorded yet.");
        }
        caveats.add("Effective sets weight primary target muscles at 1.0x and secondary synergist muscles at 0.5x across completed sets only.");
        caveats.add("Weekly hypertrophy thresholds (<3.0 undertrained, 3.0-5.9 low, 6.0-16.0 optimal, 16.1-22.0 high, >22.0 overloaded) are general guidelines and do not reflect isometric intensity or individual recovery.");
        if (unmappedCompletedSets > 0) {
            caveats.add(unmappedCompletedSets + " completed set(s) had no canonical muscle mapping and were excluded from muscle totals.");
        }

        return WeeklyMuscleAnalyticsResponse.builder()
                .startDate(startDate.toString())
                .endDate(endDate.toString())
                .totalCompletedWorkouts(totalCompletedWorkouts)
                .totalCompletedSets(totalCompletedSets)
                .totalEffectiveSets(totalEffectiveSets)
                .muscles(muscleDtos)
                .regionDistribution(regionDistribution)
                .undertrainedMuscles(undertrainedMuscles)
                .optimalMuscles(optimalMuscles)
                .overloadedMuscles(overloadedMuscles)
                .caveats(caveats)
                .build();
    }

    private Map<String, Double> computeRegionDistribution(EnumMap<MuscleRegion, Double> regionSets, double totalEffectiveSets) {
        Map<String, Double> distribution = new LinkedHashMap<>();
        if (totalEffectiveSets <= 0.0) {
            distribution.put(MuscleRegion.UPPER_BODY.name(), 0.0);
            distribution.put(MuscleRegion.CORE.name(), 0.0);
            distribution.put(MuscleRegion.LOWER_BODY.name(), 0.0);
            return distribution;
        }
        double upper = round1((regionSets.get(MuscleRegion.UPPER_BODY) / totalEffectiveSets) * 100.0);
        double core = round1((regionSets.get(MuscleRegion.CORE) / totalEffectiveSets) * 100.0);
        double lower = round1((regionSets.get(MuscleRegion.LOWER_BODY) / totalEffectiveSets) * 100.0);
        double diff = round1(100.0 - round1(upper + core + lower));
        if (diff != 0.0) {
            if (upper >= core && upper >= lower) {
                upper = round1(upper + diff);
            } else if (core >= upper && core >= lower) {
                core = round1(core + diff);
            } else {
                lower = round1(lower + diff);
            }
        }
        distribution.put(MuscleRegion.UPPER_BODY.name(), upper);
        distribution.put(MuscleRegion.CORE.name(), core);
        distribution.put(MuscleRegion.LOWER_BODY.name(), lower);
        return distribution;
    }

    private String resolveMuscleTrainingStatus(double effectiveSets) {
        if (effectiveSets < 3.0) {
            return "UNDERTRAINED";
        }
        if (effectiveSets < 6.0) {
            return "LOW";
        }
        if (effectiveSets <= 16.0) {
            return "OPTIMAL";
        }
        if (effectiveSets <= 22.0) {
            return "HIGH";
        }
        return "OVERLOADED";
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class MuscleVolumeAccumulator {
        private int primarySets;
        private int secondarySets;
        private int primaryReps;
        private int secondaryReps;
        private int primaryHoldSeconds;
        private int secondaryHoldSeconds;
        private double primaryVolumeKg;
        private double secondaryVolumeKg;

        void addPrimary(int reps, int holdSeconds, double volumeKg) {
            primarySets++;
            primaryReps += reps;
            primaryHoldSeconds += holdSeconds;
            primaryVolumeKg += volumeKg;
        }

        void addSecondary(int reps, int holdSeconds, double volumeKg) {
            secondarySets++;
            secondaryReps += reps;
            secondaryHoldSeconds += holdSeconds;
            secondaryVolumeKg += volumeKg;
        }

        double effectiveSets() {
            return primarySets + (secondarySets * 0.5);
        }
    }

    @Transactional(readOnly = true)
    public MonthlyAnalyticsResponse getMonthlyAnalytics(Long userId) {
        return getMonthlyAnalytics(userId, null);
    }

    @Transactional(readOnly = true)
    public MonthlyAnalyticsResponse getMonthlyAnalytics(Long userId, String timezone) {
        LocalDate today = userTimeResolver.today(timezone);
        ZoneId zone = userTimeResolver.resolveZone(timezone);
        LocalDate currentMonthStart = today.withDayOfMonth(1);
        LocalDate currentMonthEnd = today.with(TemporalAdjusters.lastDayOfMonth());

        LocalDate prevMonthStart = currentMonthStart.minusMonths(1);
        LocalDate prevMonthEnd = prevMonthStart.with(TemporalAdjusters.lastDayOfMonth());

        List<WorkoutSession> currMonthSessions = workoutSessionRepository.findCompletedSessionsBetweenDates(
                userId, WorkoutSessionStatus.COMPLETED, currentMonthStart, currentMonthEnd);

        List<WorkoutSession> prevMonthSessions = workoutSessionRepository.findCompletedSessionsBetweenDates(
                userId, WorkoutSessionStatus.COMPLETED, prevMonthStart, prevMonthEnd);

        int workouts = currMonthSessions.size();
        long activeDays = currMonthSessions.stream().map(WorkoutSession::getWorkoutDate).filter(Objects::nonNull).distinct().count();
        double frequency = Math.round((workouts / 4.3) * 10.0) / 10.0;

        BigDecimal currVol = calculateSessionsVolume(currMonthSessions);
        BigDecimal prevVol = calculateSessionsVolume(prevMonthSessions);
        BigDecimal volumeChange = calculatePercentage(currVol, prevVol);

        ZonedDateTime startZoned = currentMonthStart.atStartOfDay(zone);
        ZonedDateTime endZoned = currentMonthEnd.plusDays(1).atStartOfDay(zone);
        int prsAchieved = (int) personalRecordRepository.countByUserIdAndAchievedAtBetween(userId, startZoned, endZoned);

        return MonthlyAnalyticsResponse.builder()
                .completedWorkouts(workouts)
                .trainingFrequency(frequency)
                .totalVolume(currVol.setScale(2, RoundingMode.HALF_UP))
                .volumeChangePercentage(volumeChange)
                .personalRecordsAchieved(prsAchieved)
                .activeDays((int) activeDays)
                .build();
    }

    @Transactional(readOnly = true)
    public TrainingInsightsResponse getTrainingInsights(Long userId) {
        return getTrainingInsights(userId, null);
    }

    @Transactional(readOnly = true)
    public TrainingInsightsResponse getTrainingInsights(Long userId, String timezone) {
        LocalDate today = userTimeResolver.today(timezone);
        List<WorkoutSession> recentSessions = workoutSessionRepository.findByUserIdAndStatusOrderByWorkoutDateDesc(
                userId, WorkoutSessionStatus.COMPLETED);

        if (recentSessions.size() < 2) {
            return TrainingInsightsResponse.builder()
                    .hasSufficientData(false)
                    .insights(List.of("Log at least 2 completed workout sessions to generate personalized training insights."))
                    .build();
        }

        List<String> insights = new ArrayList<>();

        // 1. Weekly Volume Insight
        WeeklyAnalyticsResponse weekly = getWeeklyAnalytics(userId, timezone);
        if (weekly.getPreviousWeekVolume().compareTo(BigDecimal.ZERO) > 0 && weekly.getVolumeChangePercentage() != null) {
            BigDecimal change = weekly.getVolumeChangePercentage();
            if (change.compareTo(BigDecimal.valueOf(5)) >= 0) {
                insights.add("Your weekly training volume increased by " + change.abs() + "% compared to the previous week.");
            } else if (change.compareTo(BigDecimal.valueOf(-5)) <= 0) {
                insights.add("Your weekly training volume decreased by " + change.abs() + "% compared to the previous week.");
            }
        }

        // 2. Exercise Specific Overload Insights over the last 4 weeks
        LocalDate fourWeeksAgo = today.minusWeeks(4);
        Map<Long, List<WorkoutSession>> sessionsByExercise = new HashMap<>();

        for (WorkoutSession s : recentSessions) {
            if (s.getWorkoutDate() != null && !s.getWorkoutDate().isBefore(fourWeeksAgo)) {
                for (WorkoutSessionExercise e : s.getExercises()) {
                    if (e.getExercise() != null) {
                        sessionsByExercise.computeIfAbsent(e.getExercise().getId(), k -> new ArrayList<>()).add(s);
                    }
                }
            }
        }

        for (Map.Entry<Long, List<WorkoutSession>> entry : sessionsByExercise.entrySet()) {
            List<WorkoutSession> exerciseSessions = entry.getValue();
            if (exerciseSessions.size() < 2) continue;

            WorkoutSession newest = exerciseSessions.get(0);
            WorkoutSession oldest = exerciseSessions.get(exerciseSessions.size() - 1);

            WorkoutSessionExercise newestEx = findExerciseInSession(newest, entry.getKey());
            WorkoutSessionExercise oldestEx = findExerciseInSession(oldest, entry.getKey());

            if (newestEx == null || oldestEx == null) continue;
            String exerciseName = newestEx.getExerciseNameSnapshot();

            // Weighted improvement
            BigDecimal newestWeight = findBestWeight(newestEx);
            BigDecimal oldestWeight = findBestWeight(oldestEx);
            if (newestWeight != null && oldestWeight != null && oldestWeight.compareTo(BigDecimal.ZERO) > 0) {
                if (newestWeight.compareTo(oldestWeight) > 0) {
                    BigDecimal pct = calculatePercentage(newestWeight, oldestWeight);
                    insights.add("Your " + exerciseName + " performance improved by " + pct + "% over the last 4 weeks.");
                    continue;
                }
            }

            // Duration improvement or plateau
            Integer newestHold = findBestHold(newestEx);
            Integer oldestHold = findBestHold(oldestEx);
            if (newestHold != null && oldestHold != null) {
                if (newestHold > oldestHold) {
                    insights.add("Your " + exerciseName + " duration increased from " + oldestHold + " seconds to " + newestHold + " seconds.");
                    continue;
                } else if (exerciseSessions.size() >= 3 && newestHold.equals(oldestHold)) {
                    insights.add("Your " + exerciseName + " duration has remained unchanged for " + exerciseSessions.size() + " consecutive sessions.");
                    continue;
                }
            }

            // Reps improvement
            Integer newestReps = findBestReps(newestEx);
            Integer oldestReps = findBestReps(oldestEx);
            if (newestReps != null && oldestReps != null && newestReps > oldestReps) {
                insights.add("Your " + exerciseName + " repetitions increased from " + oldestReps + " to " + newestReps + " reps per set.");
                continue;
            }
        }

        // 3. Streak / Consistency insight
        TrainingOverviewResponse overview = getTrainingOverview(userId, timezone);
        if (overview.getCurrentStreak() >= 3) {
            insights.add("You're on a " + overview.getCurrentStreak() + "-day training streak! Keep up the consistency.");
        }

        if (insights.isEmpty()) {
            insights.add("Training performance is steady. Continue following your progressive overload schedule to unlock new records.");
        }

        return TrainingInsightsResponse.builder()
                .hasSufficientData(true)
                .insights(insights)
                .build();
    }

    // --- Coach authorized client access ---

    @Transactional(readOnly = true)
    public TrainingOverviewResponse getCoachClientOverview(Long coachId, Long clientId) {
        verifyCoachAccess(coachId, clientId);
        return getTrainingOverview(clientId);
    }

    @Transactional(readOnly = true)
    public ExerciseProgressResponse getCoachClientExerciseProgress(
            Long coachId, Long clientId, Long exerciseId, String filter) {
        verifyCoachAccess(coachId, clientId);
        return getExerciseProgress(clientId, exerciseId, filter);
    }

    private void verifyCoachAccess(Long coachId, Long clientId) {
        coachClientRelationshipRepository.findActiveRelationshipForUsers(coachId, clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You are not authorized to view this client's training progress"));
    }

    // --- Helper calculations ---

    private int calculateCurrentStreak(List<LocalDate> sortedDistinctDates, LocalDate today) {
        if (sortedDistinctDates.isEmpty()) return 0;

        LocalDate yesterday = today.minusDays(1);
        LocalDate firstDate = sortedDistinctDates.get(0);

        if (!firstDate.equals(today) && !firstDate.equals(yesterday)) {
            return 0;
        }

        int streak = 1;
        LocalDate current = firstDate;
        for (int i = 1; i < sortedDistinctDates.size(); i++) {
            LocalDate next = sortedDistinctDates.get(i);
            if (current.minusDays(1).equals(next)) {
                streak++;
                current = next;
            } else {
                break;
            }
        }
        return streak;
    }

    private int calculateLongestStreak(List<LocalDate> sortedDistinctDates) {
        if (sortedDistinctDates.isEmpty()) return 0;

        int longest = 1;
        int currentStreak = 1;
        for (int i = 1; i < sortedDistinctDates.size(); i++) {
            LocalDate prev = sortedDistinctDates.get(i - 1);
            LocalDate curr = sortedDistinctDates.get(i);
            if (prev.minusDays(1).equals(curr)) {
                currentStreak++;
                longest = Math.max(longest, currentStreak);
            } else {
                currentStreak = 1;
            }
        }
        return longest;
    }

    private BigDecimal calculatePercentage(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0 || current == null) {
            return null;
        }
        return current.subtract(previous)
                .divide(previous, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateSessionsVolume(List<WorkoutSession> sessions) {
        BigDecimal total = BigDecimal.ZERO;
        for (WorkoutSession s : sessions) {
            for (WorkoutSessionExercise e : s.getExercises()) {
                total = total.add(calculateExerciseVolume(e));
            }
        }
        return total;
    }

    private BigDecimal calculateExerciseVolume(WorkoutSessionExercise exerciseItem) {
        if (exerciseItem == null) return BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        for (WorkoutSessionSet s : exerciseItem.getSets()) {
            if (s.isCompleted() && s.getWeightKg() != null && s.getReps() != null && s.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
                volume = volume.add(s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())));
            }
        }
        return volume;
    }

    private WorkoutSessionExercise findExerciseInSession(WorkoutSession session, Long exerciseId) {
        return session.getExercises().stream()
                .filter(e -> e.getExercise() != null && e.getExercise().getId().equals(exerciseId))
                .findFirst()
                .orElse(null);
    }

    private BigDecimal findBestWeight(WorkoutSessionExercise exerciseItem) {
        if (exerciseItem == null) return null;
        BigDecimal best = null;
        for (WorkoutSessionSet s : exerciseItem.getSets()) {
            if (s.isCompleted() && s.getWeightKg() != null && s.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
                if (best == null || s.getWeightKg().compareTo(best) > 0) {
                    best = s.getWeightKg();
                }
            }
        }
        return best;
    }

    private Integer findBestReps(WorkoutSessionExercise exerciseItem) {
        if (exerciseItem == null) return null;
        Integer best = null;
        for (WorkoutSessionSet s : exerciseItem.getSets()) {
            if (s.isCompleted() && s.getReps() != null) {
                if (best == null || s.getReps() > best) {
                    best = s.getReps();
                }
            }
        }
        return best;
    }

    private Integer findBestHold(WorkoutSessionExercise exerciseItem) {
        if (exerciseItem == null) return null;
        Integer best = null;
        for (WorkoutSessionSet s : exerciseItem.getSets()) {
            if (s.isCompleted() && s.getHoldDurationSeconds() != null) {
                if (best == null || s.getHoldDurationSeconds() > best) {
                    best = s.getHoldDurationSeconds();
                }
            }
        }
        return best;
    }

    private LocalDate resolveFilterStartDate(String filter, LocalDate now) {
        if (filter == null || filter.isBlank()) return null;
        return switch (filter.trim().toLowerCase()) {
            case "7d", "7 days" -> now.minusDays(7);
            case "30d", "30 days" -> now.minusDays(30);
            case "90d", "90 days" -> now.minusDays(90);
            case "6m", "6 months" -> now.minusMonths(6);
            case "1y", "1 year" -> now.minusYears(1);
            default -> null; // All time
        };
    }
}
