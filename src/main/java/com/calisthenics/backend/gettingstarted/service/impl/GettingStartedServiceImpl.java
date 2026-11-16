package com.calisthenics.backend.gettingstarted.service.impl;

import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.gettingstarted.dto.*;
import com.calisthenics.backend.gettingstarted.entity.*;
import com.calisthenics.backend.gettingstarted.enums.*;
import com.calisthenics.backend.gettingstarted.exception.GettingStartedScheduleConflictException;
import com.calisthenics.backend.gettingstarted.repository.*;
import com.calisthenics.backend.gettingstarted.service.GettingStartedService;
import com.calisthenics.backend.notification.model.NotificationType;
import com.calisthenics.backend.notification.service.NotificationService;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.PersistentWorkoutSessionService;
import com.calisthenics.backend.workout.TodayWorkoutResolver;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSource;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workout.dto.PersistentWorkoutSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class GettingStartedServiceImpl implements GettingStartedService {

    private final GettingStartedProgramRepository programRepository;
    private final GettingStartedEnrollmentRepository enrollmentRepository;
    private final GettingStartedDayCompletionRepository dayCompletionRepository;
    private final GettingStartedProgramUserStateRepository programUserStateRepository;
    private final GettingStartedCampaignEventRepository campaignEventRepository;
    private final GettingStartedCampaignHelper campaignHelper;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;
    private final GettingStartedDayProgressRepository dayProgressRepository;
    private final GettingStartedLifecycleService lifecycleService;
    private final com.calisthenics.backend.workout.WorkoutSessionRepository workoutSessionRepository;
    private final com.calisthenics.backend.workout.TodayWorkoutResolver todayWorkoutResolver;
    private final com.calisthenics.backend.workout.PersistentWorkoutSessionService persistentWorkoutSessionService;
    @Autowired(required = false)
    private NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public GettingStartedProgramResponse getAdminProgram() {
        return programRepository.findLatest()
                .map(program -> mapToProgramResponse(program, null, false, OnboardingStatus.DISMISSED))
                .orElse(null);
    }

    @Override
    public GettingStartedProgramResponse createOrUpdateProgram(Long adminId, AdminGettingStartedProgramRequest request) {
        validateProgramRequest(request);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));

        TargetAudience audience = request.getTargetAudience() != null
                ? request.getTargetAudience()
                : TargetAudience.ALL_ATHLETES;

        GettingStartedProgram program = programRepository.findLatest().orElse(null);
        if (program == null) {
            program = GettingStartedProgram.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .targetAudience(audience)
                    .creator(admin)
                    .version(1)
                    .published(false)
                    .build();
        } else {
            program.setTitle(request.getTitle());
            program.setDescription(request.getDescription());
            program.setTargetAudience(audience);
            if (program.isPublished()) {
                // Increment version when modifying an active published campaign
                program.setVersion(program.getVersion() + 1);
            }
            program.getDays().clear();
        }

        buildProgramDays(program, request.getDays());
        GettingStartedProgram saved = programRepository.save(program);
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
    }

    @Override
    @Transactional
    public GettingStartedProgramResponse publishProgram(Long adminId) {
        GettingStartedProgram program = programRepository.findLatest()
                .orElseThrow(() -> new IllegalStateException("No Getting Started program found to publish"));

        return publishResolvedProgram(program);
    }

    @Override
    @Transactional
    public GettingStartedProgramResponse publishProgramById(Long adminId, Long programId) {
        GettingStartedProgram program = resolveAdminTargetProgram(programId, false);
        List<String> validationErrors = helper().collectValidationErrors(program);
        if (!validationErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", validationErrors));
        }
        return publishResolvedProgram(program);
    }

    private GettingStartedProgramResponse publishResolvedProgram(GettingStartedProgram program) {
        List<GettingStartedProgram> activeOthers = programRepository.findAllByPublishedTrueOrderByPublishedAtDescUpdatedAtDesc();
        if (activeOthers != null) {
            for (GettingStartedProgram other : activeOthers) {
                if (!Objects.equals(other.getId(), program.getId())) {
                    other.setPublished(false);
                    programRepository.save(other);
                }
            }
        }
        program.setPublished(true);
        program.setPublishedAt(ZonedDateTime.now());
        GettingStartedProgram saved = programRepository.save(program);
        notifyEligibleUsersOfPublishedProgram(saved);
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
    }

    private void notifyEligibleUsersOfPublishedProgram(GettingStartedProgram program) {
        if (notificationService == null || program == null || program.getId() == null) {
            return;
        }
        int version = program.getVersion() != null ? program.getVersion() : 1;
        String eventKey = "PROGRAM_AVAILABLE:" + program.getId() + ":v" + version;
        String title = "New Program Available";
        String programTitle = program.getTitle() != null && !program.getTitle().isBlank()
                ? program.getTitle()
                : "Getting Started";
        String message = "A new " + programTitle + " program is ready for you.";
        String deepLink = "getting_started/" + program.getId();

        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            if (isUserEligibleForProgram(user, program)) {
                notificationService.createNotification(
                        user,
                        program.getCreator(),
                        NotificationType.PROGRAM_AVAILABLE,
                        program.getId(),
                        "GETTING_STARTED_PROGRAM",
                        program.getId(),
                        deepLink,
                        title,
                        message,
                        eventKey
                );
            }
        }
    }

    @Override
    @Transactional
    public GettingStartedProgramResponse unpublishProgram(Long adminId) {
        GettingStartedProgram program = programRepository.findLatest()
                .orElseThrow(() -> new IllegalStateException("No Getting Started program found to unpublish"));

        program.setPublished(false);
        GettingStartedProgram saved = programRepository.save(program);
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
    }

    @Override
    @Transactional
    public GettingStartedProgramResponse unpublishProgramById(Long adminId, Long programId) {
        GettingStartedProgram program = (programId != null)
                ? programRepository.findById(programId)
                        .orElseThrow(() -> new IllegalArgumentException("Getting Started program not found: " + programId))
                : programRepository.findFirstPublished()
                        .orElseThrow(() -> new IllegalStateException("No active published Getting Started campaign found to unpublish"));

        if (!program.isPublished()) {
            throw new IllegalStateException("Getting Started program '" + program.getTitle() + "' is not currently published.");
        }
        program.setPublished(false);
        GettingStartedProgram saved = programRepository.save(program);
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
    }

    @Override
    public void deleteProgram(Long adminId) {
        GettingStartedProgram program = programRepository.findLatest().orElse(null);
        if (program != null) {
            programRepository.delete(program);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public GettingStartedProgramResponse getPublishedProgram(Long userId) {
        GettingStartedProgram program = programRepository.findFirstPublished().orElse(null);
        if (program == null) {
            return null;
        }

        boolean eligible = false;
        OnboardingStatus dismissalState = OnboardingStatus.NOT_SHOWN;
        GettingStartedEnrollment enrollment = null;

        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                eligible = isUserEligibleForProgram(user, program);
                dismissalState = programUserStateRepository
                        .findByUserIdAndProgramIdAndProgramVersion(userId, program.getId(), program.getVersion())
                        .map(GettingStartedProgramUserState::getState)
                        .orElse(OnboardingStatus.NOT_SHOWN);
                enrollment = enrollmentRepository.findByUserIdAndProgramId(userId, program.getId()).orElse(null);
            }
        }

        return mapToProgramResponse(program, enrollment, eligible, dismissalState);
    }

    @Override
    public GettingStartedProgramResponse updateDismissalStatus(Long userId, OnboardingStatus status) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("Dismissal status is required");
        }

        GettingStartedProgram program = programRepository.findFirstPublished()
                .or(programRepository::findLatest)
                .orElseThrow(() -> new IllegalStateException("No Getting Started program found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        GettingStartedProgramUserState userState = programUserStateRepository
                .findByUserIdAndProgramIdAndProgramVersion(userId, program.getId(), program.getVersion())
                .orElseGet(() -> GettingStartedProgramUserState.builder()
                        .user(user)
                        .program(program)
                        .programVersion(program.getVersion())
                        .build());

        boolean previouslyNotShown = userState.getState() == OnboardingStatus.NOT_SHOWN;
        userState.setState(status);
        programUserStateRepository.save(userState);

        if (previouslyNotShown
                && (status == OnboardingStatus.COMPACT_BANNER || status == OnboardingStatus.DISMISSED)) {
            recordEventIfAbsent(program, user, GettingStartedCampaignEventType.MODAL_DISMISSAL);
        }

        boolean eligible = isUserEligibleForProgram(user, program);
        GettingStartedEnrollment enrollment = enrollmentRepository.findByUserIdAndProgramId(userId, program.getId()).orElse(null);

        return mapToProgramResponse(program, enrollment, eligible, status);
    }

    @Override
    public GettingStartedEnrollmentResponse enrollAthlete(Long userId) {
        return enrollAthlete(userId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public GettingStartedEnrollPreviewResponse previewEnrollment(Long userId, LocalDate startDate) {
        GettingStartedProgram program = programRepository.findFirstPublished()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Getting Started program is not published"));
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.now();
        GettingStartedEnrollment existing = enrollmentRepository
                .findByUserIdAndProgramId(userId, program.getId())
                .orElse(null);

        List<LocalDate> plannedDates = List.of(
                effectiveStartDate,
                effectiveStartDate.plusDays(1),
                effectiveStartDate.plusDays(2)
        );
        Set<Integer> completedDays = existing != null ? completedDayNumbers(existing) : Collections.emptySet();
        List<GettingStartedScheduleConflictResponse> conflicts = detectScheduleConflicts(
                userId, program, effectiveStartDate, completedDays);

        return GettingStartedEnrollPreviewResponse.builder()
                .programId(program.getId())
                .programTitle(program.getTitle())
                .alreadyEnrolled(existing != null)
                .alreadyAddedToWorkoutPlan(existing != null && existing.isAddedToWorkoutPlan())
                .startDate(effectiveStartDate)
                .plannedDates(plannedDates)
                .hasConflicts(!conflicts.isEmpty())
                .conflicts(conflicts)
                .build();
    }

    @Override
    public GettingStartedEnrollmentResponse enrollAthlete(Long userId, EnrollGettingStartedRequest request) {
        GettingStartedProgram program = programRepository.findFirstPublished()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Getting Started program is not published"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        boolean addToWorkoutPlan = request != null && Boolean.TRUE.equals(request.getAddToWorkoutPlan());
        boolean confirmConflicts = request != null && Boolean.TRUE.equals(request.getConfirmConflicts());
        LocalDate effectiveStartDate = request != null && request.getStartDate() != null
                ? request.getStartDate()
                : LocalDate.now();

        GettingStartedEnrollment existing = enrollmentRepository
                .findByUserIdAndProgramId(userId, program.getId())
                .orElse(null);

        if (existing != null) {
            if (!addToWorkoutPlan
                    || existing.isAddedToWorkoutPlan()
                    || existing.getStatus() == GettingStartedEnrollmentStatus.COMPLETED) {
                if (lifecycleService != null) {
                    lifecycleService.initializeDayProgressForEnrollment(existing, existing.getStartDate());
                }
                return mapToEnrollmentResponse(existing);
            }

            Set<Integer> completedDays = completedDayNumbers(existing);
            List<GettingStartedScheduleConflictResponse> conflicts = detectScheduleConflicts(
                    userId, program, effectiveStartDate, completedDays);
            if (!conflicts.isEmpty() && !confirmConflicts) {
                throw new GettingStartedScheduleConflictException(
                        "Your Workout Plan already has workouts scheduled on overlapping dates.",
                        conflicts
                );
            }

            existing.setAddedToWorkoutPlan(true);
            if (existing.getStartDate() == null) {
                existing.setStartDate(effectiveStartDate);
            }
            GettingStartedEnrollment updated = enrollmentRepository.save(existing);
            scheduleDailySessionsForEnrollment(updated, program, effectiveStartDate);
            return mapToEnrollmentResponse(updated);
        }

        if (addToWorkoutPlan) {
            List<GettingStartedScheduleConflictResponse> conflicts = detectScheduleConflicts(
                    userId, program, effectiveStartDate, Collections.emptySet());
            if (!conflicts.isEmpty() && !confirmConflicts) {
                throw new GettingStartedScheduleConflictException(
                        "Your Workout Plan already has workouts scheduled on overlapping dates.",
                        conflicts
                );
            }
        }

        GettingStartedEnrollment newEnrollment = GettingStartedEnrollment.builder()
                .user(user)
                .program(program)
                .programVersion(program.getVersion())
                .status(GettingStartedEnrollmentStatus.NOT_STARTED)
                .addedToWorkoutPlan(addToWorkoutPlan)
                .startDate(addToWorkoutPlan ? effectiveStartDate : null)
                .build();

        GettingStartedEnrollment saved = enrollmentRepository.save(newEnrollment);
        if (lifecycleService != null) {
            lifecycleService.initializeDayProgressForEnrollment(saved, saved.getStartDate());
        }
        if (addToWorkoutPlan) {
            scheduleDailySessionsForEnrollment(saved, program, effectiveStartDate);
        }
        return mapToEnrollmentResponse(saved);
    }

    @Override
    public PersistentWorkoutSessionResponse startDaySession(Long userId, Integer dayNumber, LocalDate workoutDate) {
        if (dayNumber == null || dayNumber < 1 || dayNumber > GettingStartedCampaignHelper.EXPECTED_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Day number must be 1, 2, or 3");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        GettingStartedEnrollment enrollment = resolveEnrollmentForUser(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Enroll in the Getting Started program before starting a day."));

        GettingStartedProgram program = enrollment.getProgram();
        GettingStartedProgramDay programDay = program.getDays().stream()
                .filter(d -> dayNumber.equals(d.getDayNumber()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Program day " + dayNumber + " not found"));

        GettingStartedDayProgress existingProgress = dayProgressRepository != null
                ? dayProgressRepository.findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNumber).orElse(null)
                : null;
        boolean alreadyCompleted = (existingProgress != null
                && existingProgress.getStatus() == GettingStartedDayStatus.COMPLETED)
                || completedDayNumbers(enrollment).contains(dayNumber);
        if (alreadyCompleted) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Day " + dayNumber + " has already been completed.");
        }

        Optional<WorkoutSession> activeOrScheduled = workoutSessionRepository
                .findFirstByGettingStartedEnrollmentIdAndGettingStartedDayNumberAndStatusInOrderByIdDesc(
                        enrollment.getId(),
                        dayNumber,
                        List.of(WorkoutSessionStatus.IN_PROGRESS, WorkoutSessionStatus.SCHEDULED)
                );

        WorkoutSession session;
        if (activeOrScheduled.isPresent()) {
            session = activeOrScheduled.get();
            if (session.getStatus() == WorkoutSessionStatus.SCHEDULED) {
                session.setStatus(WorkoutSessionStatus.IN_PROGRESS);
                if (session.getStartedAt() == null) {
                    session.setStartedAt(ZonedDateTime.now());
                }
            }
            if ((session.getExercises() == null || session.getExercises().isEmpty()) && lifecycleService != null) {
                lifecycleService.populateGettingStartedExercises(session, programDay);
            }
            session = workoutSessionRepository.saveAndFlush(session);
        } else {
            LocalDate sessionDate = existingProgress != null && existingProgress.getScheduledDate() != null
                    ? existingProgress.getScheduledDate()
                    : (workoutDate != null ? workoutDate : LocalDate.now());
            session = WorkoutSession.builder()
                    .user(user)
                    .source(WorkoutSessionSource.GETTING_STARTED)
                    .gettingStartedEnrollment(enrollment)
                    .gettingStartedDayNumber(dayNumber)
                    .title(buildSessionTitle(programDay))
                    .workoutDate(sessionDate)
                    .status(WorkoutSessionStatus.IN_PROGRESS)
                    .startedAt(ZonedDateTime.now())
                    .build();
            if (lifecycleService != null) {
                lifecycleService.populateGettingStartedExercises(session, programDay);
            }
            session = workoutSessionRepository.saveAndFlush(session);
        }

        if (lifecycleService != null) {
            lifecycleService.markDayInProgress(enrollment, dayNumber, session);
        }
        return persistentWorkoutSessionService.toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GettingStartedEnrollmentResponse> getUserEnrollments(Long userId) {
        return enrollmentRepository.findAllByUserIdOrderByEnrolledAtDesc(userId).stream()
                .map(this::mapToEnrollmentResponse)
                .collect(Collectors.toList());
    }

    @Override
    public GettingStartedEnrollmentResponse completeDay(Long userId, Integer dayNumber) {
        if (dayNumber == null || dayNumber < 1 || dayNumber > 3) {
            throw new IllegalArgumentException("Day number must be 1, 2, or 3");
        }

        GettingStartedEnrollment enrollment = resolveEnrollmentForUser(userId)
                .orElseThrow(() -> new IllegalStateException("Athlete is not enrolled in the Getting Started program"));

        Optional<GettingStartedDayCompletion> existingCompletion =
                dayCompletionRepository.findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNumber);
        if (existingCompletion.isPresent()) {
            return mapToEnrollmentResponse(enrollment);
        }

        WorkoutSession latestSession = workoutSessionRepository != null
                ? workoutSessionRepository
                        .findFirstByGettingStartedEnrollmentIdAndGettingStartedDayNumberOrderByIdDesc(
                                enrollment.getId(), dayNumber)
                        .orElse(null)
                : null;
        if (latestSession == null
                || lifecycleService == null
                || !lifecycleService.qualifiesForDayCompletion(latestSession)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Complete all required exercises in Day " + dayNumber + " workout session before completing the day.");
        }

        lifecycleService.onWorkoutSessionFinished(latestSession);
        GettingStartedEnrollment refreshed = enrollmentRepository.findById(enrollment.getId()).orElse(enrollment);
        return mapToEnrollmentResponse(refreshed);
    }

    private Optional<GettingStartedEnrollment> resolveEnrollmentForUser(Long userId) {
        Optional<GettingStartedProgram> published = programRepository.findFirstPublished();
        if (published.isPresent()) {
            Optional<GettingStartedEnrollment> byPublished = enrollmentRepository
                    .findByUserIdAndProgramId(userId, published.get().getId());
            if (byPublished.isPresent()) {
                return byPublished;
            }
        }
        List<GettingStartedEnrollment> all = enrollmentRepository.findAllByUserIdOrderByEnrolledAtDesc(userId);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getFirst());
    }

    private void scheduleDailySessionsForEnrollment(
            GettingStartedEnrollment enrollment,
            GettingStartedProgram program,
            LocalDate startDate
    ) {
        if (workoutSessionRepository == null) {
            return;
        }
        Set<Integer> completedDays = completedDayNumbers(enrollment);
        Map<Integer, GettingStartedProgramDay> daysByNum = program.getDays().stream()
                .collect(Collectors.toMap(GettingStartedProgramDay::getDayNumber, d -> d, (a, b) -> a));

        for (int dayNumber = 1; dayNumber <= GettingStartedCampaignHelper.EXPECTED_DAYS; dayNumber++) {
            LocalDate scheduledDate = startDate.plusDays(dayNumber - 1L);
            GettingStartedDayProgress progress = dayProgressRepository != null
                    ? dayProgressRepository.findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNumber).orElse(null)
                    : null;
            if (progress != null && progress.getScheduledDate() == null) {
                progress.setScheduledDate(scheduledDate);
                dayProgressRepository.save(progress);
            }

            if (completedDays.contains(dayNumber)
                    || (progress != null && progress.getStatus() == GettingStartedDayStatus.COMPLETED)) {
                continue;
            }

            Optional<WorkoutSession> existingSession = workoutSessionRepository
                    .findFirstByGettingStartedEnrollmentIdAndGettingStartedDayNumberAndStatusInOrderByIdDesc(
                            enrollment.getId(),
                            dayNumber,
                            List.of(WorkoutSessionStatus.SCHEDULED, WorkoutSessionStatus.IN_PROGRESS)
                    );
            if (existingSession.isPresent()) {
                if (progress != null && progress.getWorkoutSession() == null) {
                    progress.setWorkoutSession(existingSession.get());
                    dayProgressRepository.save(progress);
                }
                continue;
            }

            GettingStartedProgramDay programDay = daysByNum.get(dayNumber);
            WorkoutSession scheduled = WorkoutSession.builder()
                    .user(enrollment.getUser())
                    .source(WorkoutSessionSource.GETTING_STARTED)
                    .gettingStartedEnrollment(enrollment)
                    .gettingStartedDayNumber(dayNumber)
                    .title(buildSessionTitle(programDay, dayNumber))
                    .workoutDate(scheduledDate)
                    .status(WorkoutSessionStatus.SCHEDULED)
                    .build();
            WorkoutSession savedSession = workoutSessionRepository.save(scheduled);

            if (progress != null) {
                progress.setScheduledDate(scheduledDate);
                progress.setWorkoutSession(savedSession);
                dayProgressRepository.save(progress);
            } else if (dayProgressRepository != null) {
                dayProgressRepository.save(GettingStartedDayProgress.builder()
                        .enrollment(enrollment)
                        .dayNumber(dayNumber)
                        .status(GettingStartedDayStatus.NOT_STARTED)
                        .scheduledDate(scheduledDate)
                        .workoutSession(savedSession)
                        .build());
            }
        }
    }

    private List<GettingStartedScheduleConflictResponse> detectScheduleConflicts(
            Long userId,
            GettingStartedProgram program,
            LocalDate startDate,
            Set<Integer> completedDays
    ) {
        if (workoutSessionRepository == null || todayWorkoutResolver == null) {
            return Collections.emptyList();
        }
        Map<Integer, GettingStartedProgramDay> daysByNum = program.getDays().stream()
                .collect(Collectors.toMap(GettingStartedProgramDay::getDayNumber, d -> d, (a, b) -> a));

        List<GettingStartedScheduleConflictResponse> conflicts = new ArrayList<>();
        for (int dayNumber = 1; dayNumber <= GettingStartedCampaignHelper.EXPECTED_DAYS; dayNumber++) {
            if (completedDays.contains(dayNumber)) {
                continue;
            }
            LocalDate date = startDate.plusDays(dayNumber - 1L);
            GettingStartedProgramDay gsDay = daysByNum.get(dayNumber);
            String gsDayTitle = gsDay != null ? gsDay.getTitle() : "Day " + dayNumber;

            Set<Long> sessionPlanIdsOnDate = new HashSet<>();
            List<WorkoutSession> existingSessions = workoutSessionRepository
                    .findByUserIdAndWorkoutDateOrderByIdAsc(userId, date);
            for (WorkoutSession session : existingSessions) {
                if (session.getSource() == WorkoutSessionSource.GETTING_STARTED) {
                    continue;
                }
                if (session.getStatus() != WorkoutSessionStatus.SCHEDULED
                        && session.getStatus() != WorkoutSessionStatus.IN_PROGRESS) {
                    continue;
                }
                if (session.getWorkoutPlanAssignment() != null
                        && session.getWorkoutPlanAssignment().getStatus() != WorkoutPlanAssignmentStatus.ACTIVE) {
                    continue;
                }
                if (session.getWorkoutPlan() != null) {
                    sessionPlanIdsOnDate.add(session.getWorkoutPlan().getId());
                }
                conflicts.add(GettingStartedScheduleConflictResponse.builder()
                        .date(date)
                        .gettingStartedDayNumber(dayNumber)
                        .gettingStartedDayTitle(gsDayTitle)
                        .existingWorkoutTitle(session.getTitle())
                        .existingSource(session.getSource())
                        .workoutSessionId(session.getId())
                        .workoutPlanId(session.getWorkoutPlan() != null ? session.getWorkoutPlan().getId() : null)
                        .build());
            }

            TodayWorkoutResolver.ResolutionCandidates resolved = todayWorkoutResolver.resolveCandidates(userId, date);
            for (TodayWorkoutResolver.ResolvedWorkout candidate : resolved.candidates()) {
                if (candidate.day() == null || candidate.day().isRestDay()) {
                    continue;
                }
                if (candidate.plan() != null && sessionPlanIdsOnDate.contains(candidate.plan().getId())) {
                    continue;
                }
                String planName = candidate.plan() != null ? candidate.plan().getName() : "Workout Plan";
                String dayFocus = candidate.day().getSessionType() != null
                        ? candidate.day().getSessionType().name()
                        : "Workout";
                conflicts.add(GettingStartedScheduleConflictResponse.builder()
                        .date(date)
                        .gettingStartedDayNumber(dayNumber)
                        .gettingStartedDayTitle(gsDayTitle)
                        .existingWorkoutTitle(planName + " · " + dayFocus)
                        .existingSource(candidate.assignment() != null
                                ? WorkoutSessionSource.COACH
                                : WorkoutSessionSource.PERSONAL)
                        .workoutPlanId(candidate.plan() != null ? candidate.plan().getId() : null)
                        .build());
            }
        }
        return conflicts;
    }

    private Set<Integer> completedDayNumbers(GettingStartedEnrollment enrollment) {
        Set<Integer> completed = new HashSet<>();
        if (enrollment.getCompletions() != null) {
            enrollment.getCompletions().forEach(c -> completed.add(c.getDayNumber()));
        }
        if (dayCompletionRepository != null && enrollment.getId() != null) {
            dayCompletionRepository.findByEnrollmentIdOrderByDayNumberAsc(enrollment.getId())
                    .forEach(c -> completed.add(c.getDayNumber()));
        }
        return completed;
    }

    private String buildSessionTitle(GettingStartedProgramDay day) {
        return buildSessionTitle(day, day != null ? day.getDayNumber() : 1);
    }

    private String buildSessionTitle(GettingStartedProgramDay day, int fallbackDayNumber) {
        String rawTitle = day != null && day.getTitle() != null && !day.getTitle().isBlank()
                ? day.getTitle().trim()
                : "Day " + fallbackDayNumber;
        if (rawTitle.toLowerCase(Locale.US).startsWith("getting started")) {
            return rawTitle;
        }
        return "Getting Started · " + rawTitle;
    }

    private boolean isUserEligibleForProgram(User user, GettingStartedProgram program) {
        // Admin accounts must never receive the athlete onboarding modal
        if (user.getRole() == Role.ROLE_ADMIN) {
            return false;
        }

        TargetAudience audience = program.getTargetAudience() != null
                ? program.getTargetAudience()
                : TargetAudience.ALL_ATHLETES;

        return switch (audience) {
            case ALL_ATHLETES -> true;
            case LAST_30_DAYS -> user.getCreatedAt() != null
                    && !user.getCreatedAt().isBefore(ZonedDateTime.now().minusDays(30));
            case LAST_14_DAYS -> user.getCreatedAt() != null
                    && !user.getCreatedAt().isBefore(ZonedDateTime.now().minusDays(14));
        };
    }

    private void validateProgramRequest(AdminGettingStartedProgramRequest request) {
        if (request.getDays() == null || request.getDays().size() != 3) {
            throw new IllegalArgumentException("Program must contain exactly 3 days");
        }

        Set<Integer> dayNumbers = new HashSet<>();
        for (AdminGettingStartedDayRequest day : request.getDays()) {
            if (day.getDayNumber() == null || day.getDayNumber() < 1 || day.getDayNumber() > 3) {
                throw new IllegalArgumentException("Invalid day number: " + day.getDayNumber());
            }
            if (!dayNumbers.add(day.getDayNumber())) {
                throw new IllegalArgumentException("Duplicate day number: " + day.getDayNumber());
            }
            if (day.getExercises() == null || day.getExercises().isEmpty()) {
                throw new IllegalArgumentException("Day " + day.getDayNumber() + " must contain at least one exercise");
            }
        }
    }

    private void buildProgramDays(GettingStartedProgram program, List<AdminGettingStartedDayRequest> dayRequests) {
        List<AdminGettingStartedDayRequest> sortedDays = new ArrayList<>(dayRequests);
        sortedDays.sort(Comparator.comparingInt(AdminGettingStartedDayRequest::getDayNumber));

        for (AdminGettingStartedDayRequest dayReq : sortedDays) {
            GettingStartedProgramDay day = GettingStartedProgramDay.builder()
                    .program(program)
                    .dayNumber(dayReq.getDayNumber())
                    .title(dayReq.getTitle())
                    .description(dayReq.getDescription())
                    .build();

            int order = 0;
            for (AdminGettingStartedExerciseRequest exReq : dayReq.getExercises()) {
                Exercise catalogExercise = null;
                if (exReq.getExerciseId() != null) {
                    catalogExercise = exerciseRepository.findById(exReq.getExerciseId()).orElse(null);
                }

                String exerciseName = exReq.getExerciseName();
                if ((exerciseName == null || exerciseName.isBlank()) && catalogExercise != null) {
                    exerciseName = catalogExercise.getName();
                }

                GettingStartedProgramExercise exercise = GettingStartedProgramExercise.builder()
                        .programDay(day)
                        .exercise(catalogExercise)
                        .exerciseName(exerciseName != null ? exerciseName : "Exercise")
                        .sets(exReq.getSets() != null ? exReq.getSets() : 3)
                        .reps(exReq.getReps())
                        .durationSeconds(exReq.getDurationSeconds())
                        .restSeconds(exReq.getRestSeconds() != null ? exReq.getRestSeconds() : 60)
                        .instructions(exReq.getInstructions())
                        .displayOrder(exReq.getDisplayOrder() != null ? exReq.getDisplayOrder() : order++)
                        .build();

                day.getExercises().add(exercise);
            }
            program.getDays().add(day);
        }
    }

    private GettingStartedProgramResponse mapToProgramResponse(
            GettingStartedProgram program,
            GettingStartedEnrollment enrollment,
            boolean eligible,
            OnboardingStatus userDismissalState
    ) {
        GettingStartedEnrollmentResponse enrollmentResponse =
                enrollment != null ? mapToEnrollmentResponse(enrollment) : null;
        Map<Integer, GettingStartedDayProgressResponse> progressByDay = enrollmentResponse != null
                ? enrollmentResponse.getDayProgress().stream()
                        .collect(Collectors.toMap(
                                GettingStartedDayProgressResponse::getDayNumber,
                                item -> item,
                                (a, b) -> a))
                : Collections.emptyMap();

        List<GettingStartedDayResponse> dayResponses = program.getDays().stream()
                .sorted(Comparator.comparingInt(GettingStartedProgramDay::getDayNumber))
                .map(day -> {
                    GettingStartedDayProgressResponse dp = progressByDay.get(day.getDayNumber());
                    GettingStartedDayStatus dayStatus = dp != null && dp.getStatus() != null
                            ? dp.getStatus()
                            : GettingStartedDayStatus.NOT_STARTED;
                    return GettingStartedDayResponse.builder()
                            .id(day.getId())
                            .dayNumber(day.getDayNumber())
                            .title(day.getTitle())
                            .description(day.getDescription())
                            .status(dayStatus)
                            .completed(dayStatus == GettingStartedDayStatus.COMPLETED)
                            .scheduledDate(dp != null ? dp.getScheduledDate() : null)
                            .workoutSessionId(dp != null ? dp.getWorkoutSessionId() : null)
                            .startedAt(dp != null ? dp.getStartedAt() : null)
                            .completedAt(dp != null ? dp.getCompletedAt() : null)
                            .exercises(day.getExercises().stream()
                                    .sorted(Comparator.comparingInt(GettingStartedProgramExercise::getDisplayOrder))
                                    .map(ex -> GettingStartedExerciseResponse.builder()
                                            .id(ex.getId())
                                            .exerciseId(ex.getExercise() != null ? ex.getExercise().getId() : null)
                                            .exerciseName(ex.getExerciseName())
                                            .sets(ex.getSets())
                                            .reps(ex.getReps())
                                            .durationSeconds(ex.getDurationSeconds())
                                            .restSeconds(ex.getRestSeconds())
                                            .instructions(ex.getInstructions())
                                            .displayOrder(ex.getDisplayOrder())
                                            .build())
                                    .collect(Collectors.toList()))
                            .build();
                })
                .collect(Collectors.toList());

        return GettingStartedProgramResponse.builder()
                .id(program.getId())
                .title(program.getTitle())
                .description(program.getDescription())
                .targetAudience(program.getTargetAudience())
                .published(program.isPublished())
                .version(program.getVersion())
                .creatorId(program.getCreator() != null ? program.getCreator().getId() : null)
                .creatorName(program.getCreator() != null ? program.getCreator().getFullName() : null)
                .createdAt(program.getCreatedAt())
                .updatedAt(program.getUpdatedAt())
                .publishedAt(program.getPublishedAt())
                .eligible(eligible)
                .userDismissalState(userDismissalState)
                .days(dayResponses)
                .enrollment(enrollmentResponse)
                .build();
    }

    private GettingStartedEnrollmentResponse mapToEnrollmentResponse(GettingStartedEnrollment enrollment) {
        Map<Integer, GettingStartedDayCompletion> completionByDay = new HashMap<>();
        if (enrollment.getCompletions() != null) {
            enrollment.getCompletions().forEach(c -> completionByDay.put(c.getDayNumber(), c));
        }
        if (dayCompletionRepository != null && enrollment.getId() != null) {
            dayCompletionRepository.findByEnrollmentIdOrderByDayNumberAsc(enrollment.getId())
                    .forEach(c -> completionByDay.put(c.getDayNumber(), c));
        }

        Map<Integer, GettingStartedDayProgress> progressByDay = new HashMap<>();
        if (enrollment.getDayProgress() != null) {
            enrollment.getDayProgress().forEach(p -> progressByDay.put(p.getDayNumber(), p));
        }
        if (dayProgressRepository != null && enrollment.getId() != null) {
            dayProgressRepository.findByEnrollmentIdOrderByDayNumberAsc(enrollment.getId())
                    .forEach(p -> progressByDay.put(p.getDayNumber(), p));
        }

        Map<Integer, GettingStartedProgramDay> programDaysByNumber = new HashMap<>();
        if (enrollment.getProgram() != null && enrollment.getProgram().getDays() != null) {
            enrollment.getProgram().getDays()
                    .forEach(d -> programDaysByNumber.put(d.getDayNumber(), d));
        }

        List<GettingStartedDayProgressResponse> dayProgressResponses = new ArrayList<>();
        List<Integer> completedDays = new ArrayList<>();
        boolean anyStarted = false;

        for (int dayNumber = 1; dayNumber <= GettingStartedCampaignHelper.EXPECTED_DAYS; dayNumber++) {
            GettingStartedDayCompletion completion = completionByDay.get(dayNumber);
            GettingStartedDayProgress progress = progressByDay.get(dayNumber);
            GettingStartedProgramDay programDay = programDaysByNumber.get(dayNumber);

            GettingStartedDayStatus dayStatus;
            if (completion != null || (progress != null && progress.getStatus() == GettingStartedDayStatus.COMPLETED)) {
                dayStatus = GettingStartedDayStatus.COMPLETED;
                completedDays.add(dayNumber);
                anyStarted = true;
            } else if (progress != null && progress.getStatus() == GettingStartedDayStatus.IN_PROGRESS) {
                dayStatus = GettingStartedDayStatus.IN_PROGRESS;
                anyStarted = true;
            } else {
                dayStatus = GettingStartedDayStatus.NOT_STARTED;
            }

            Long sessionId = resolveNavigableSessionId(dayStatus, progress, completion);
            LocalDate scheduledDate = progress != null && progress.getScheduledDate() != null
                    ? progress.getScheduledDate()
                    : (enrollment.isAddedToWorkoutPlan() && enrollment.getStartDate() != null
                            ? enrollment.getStartDate().plusDays(dayNumber - 1L)
                            : null);

            dayProgressResponses.add(GettingStartedDayProgressResponse.builder()
                    .dayNumber(dayNumber)
                    .title(programDay != null ? programDay.getTitle() : "Day " + dayNumber)
                    .status(dayStatus)
                    .scheduledDate(scheduledDate)
                    .workoutSessionId(sessionId)
                    .startedAt(progress != null ? progress.getStartedAt() : null)
                    .completedAt(completion != null ? completion.getCompletedAt()
                            : (progress != null ? progress.getCompletedAt() : null))
                    .build());
        }

        GettingStartedEnrollmentStatus effectiveStatus = enrollment.getStatus();
        if (completedDays.size() >= GettingStartedCampaignHelper.EXPECTED_DAYS) {
            effectiveStatus = GettingStartedEnrollmentStatus.COMPLETED;
        } else if (effectiveStatus == GettingStartedEnrollmentStatus.ACTIVE) {
            effectiveStatus = anyStarted
                    ? GettingStartedEnrollmentStatus.IN_PROGRESS
                    : GettingStartedEnrollmentStatus.NOT_STARTED;
        }

        return GettingStartedEnrollmentResponse.builder()
                .id(enrollment.getId())
                .programId(enrollment.getProgram().getId())
                .programTitle(enrollment.getProgram().getTitle())
                .programDescription(enrollment.getProgram().getDescription())
                .programVersion(enrollment.getProgramVersion())
                .status(effectiveStatus)
                .addedToWorkoutPlan(enrollment.isAddedToWorkoutPlan())
                .startDate(enrollment.getStartDate())
                .enrolledAt(enrollment.getEnrolledAt())
                .startedAt(enrollment.getStartedAt())
                .completedAt(enrollment.getCompletedAt())
                .completedDays(completedDays)
                .dayProgress(dayProgressResponses)
                .completedDaysCount(completedDays.size())
                .totalDays(GettingStartedCampaignHelper.EXPECTED_DAYS)
                .isCompleted(effectiveStatus == GettingStartedEnrollmentStatus.COMPLETED)
                .build();
    }

    private Long resolveNavigableSessionId(
            GettingStartedDayStatus dayStatus,
            GettingStartedDayProgress progress,
            GettingStartedDayCompletion completion
    ) {
        if (dayStatus == GettingStartedDayStatus.COMPLETED) {
            if (completion != null && completion.getWorkoutSession() != null) {
                return completion.getWorkoutSession().getId();
            }
            if (progress != null && progress.getWorkoutSession() != null) {
                return progress.getWorkoutSession().getId();
            }
            return null;
        }
        if (progress != null && progress.getWorkoutSession() != null) {
            WorkoutSessionStatus sessionStatus = progress.getWorkoutSession().getStatus();
            if (sessionStatus == WorkoutSessionStatus.IN_PROGRESS
                    || sessionStatus == WorkoutSessionStatus.SCHEDULED) {
                return progress.getWorkoutSession().getId();
            }
        }
        return null;
    }

    @Override
    public GettingStartedProgramResponse createNewProgram(Long adminId, AdminGettingStartedProgramRequest request) {
        validateProgramStrictly(request);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        TargetAudience audience = request.getTargetAudience() != null
                ? request.getTargetAudience()
                : TargetAudience.ALL_ATHLETES;

        GettingStartedProgram program = GettingStartedProgram.builder()
                .creator(admin)
                .title(request.getTitle().trim())
                .description(request.getDescription() != null ? request.getDescription().trim() : null)
                .targetAudience(audience)
                .published(false)
                .version(1)
                .build();
        buildProgramDays(program, request.getDays());
        GettingStartedProgram saved = programRepository.save(program);
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
    }

    @Override
    public GettingStartedProgramResponse updateProgramById(Long adminId, Long programId, AdminGettingStartedProgramRequest request) {
        validateProgramStrictly(request);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin user not found"));
        GettingStartedProgram program = resolveAdminTargetProgram(programId, true);

        program.setCreator(admin);
        program.setTitle(request.getTitle().trim());
        program.setDescription(request.getDescription() != null ? request.getDescription().trim() : null);
        if (request.getTargetAudience() != null) {
            program.setTargetAudience(request.getTargetAudience());
        }
        program.setVersion(program.getVersion() + 1);
        program.getDays().clear();
        programRepository.saveAndFlush(program);

        buildProgramDays(program, request.getDays());
        GettingStartedProgram saved = programRepository.save(program);
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
    }

    @Override
    @Transactional(readOnly = true)
    public GettingStartedProgramResponse getProgramById(Long programId) {
        GettingStartedProgram program = resolveAdminTargetProgram(programId, false);
        return mapToProgramResponse(program, null, false, OnboardingStatus.DISMISSED);
    }

    @Override
    public GettingStartedProgramResponse configureAudience(Long adminId, Long programId, TargetAudience targetAudience) {
        if (targetAudience == null) {
            throw new IllegalArgumentException("Target audience is required");
        }
        GettingStartedProgram program = resolveAdminTargetProgram(programId, false);
        program.setTargetAudience(targetAudience);
        GettingStartedProgram saved = programRepository.save(program);
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
    }

    @Override
    @Transactional(readOnly = true)
    public GettingStartedProgramPreviewResponse previewProgram(Long programId) {
        GettingStartedProgram program = resolveAdminTargetProgram(programId, false);
        List<String> validationErrors = helper().collectValidationErrors(program);
        TargetAudience audience = program.getTargetAudience() != null
                ? program.getTargetAudience()
                : TargetAudience.ALL_ATHLETES;
        long eligible = helper().countEligibleAthletes(audience, userRepository);
        long totalAthletes = userRepository.countByRole(Role.ROLE_USER);

        return GettingStartedProgramPreviewResponse.builder()
                .program(mapToProgramResponse(program, null, false, OnboardingStatus.DISMISSED))
                .valid(validationErrors.isEmpty())
                .validationErrors(validationErrors)
                .targetAudience(audience)
                .audienceLabel(helper().audienceLabel(audience))
                .eligibleAthleteCount(eligible)
                .totalAthleteCount(totalAthletes)
                .estimatedAudienceSize(eligible)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GettingStartedCampaignSummaryResponse> getActiveCampaigns() {
        List<GettingStartedProgram> activePrograms = programRepository.findAllByPublishedTrueOrderByPublishedAtDescUpdatedAtDesc();
        if (activePrograms == null) {
            return Collections.emptyList();
        }
        return activePrograms.stream()
                .map(program -> {
                    TargetAudience audience = program.getTargetAudience() != null
                            ? program.getTargetAudience()
                            : TargetAudience.ALL_ATHLETES;
                    GettingStartedProgramResponse mapped = mapToProgramResponse(program, null, false, OnboardingStatus.DISMISSED);
                    return GettingStartedCampaignSummaryResponse.builder()
                            .programId(program.getId())
                            .title(program.getTitle())
                            .description(program.getDescription())
                            .published(program.isPublished())
                            .version(program.getVersion())
                            .targetAudience(audience)
                            .audienceLabel(helper().audienceLabel(audience))
                            .eligibleAthleteCount(helper().countEligibleAthletes(audience, userRepository))
                            .enrollments(enrollmentRepository.countByProgramId(program.getId()))
                            .publishedAt(program.getPublishedAt())
                            .updatedAt(program.getUpdatedAt())
                            .days(mapped.getDays())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GettingStartedCampaignAnalyticsResponse getCampaignAnalytics(Long programId) {
        GettingStartedProgram program = (programId != null)
                ? programRepository.findById(programId)
                        .orElseThrow(() -> new IllegalArgumentException("Getting Started program not found: " + programId))
                : programRepository.findFirstPublished()
                        .or(programRepository::findLatest)
                        .orElseThrow(() -> new IllegalStateException("No Getting Started campaign found"));
        return helper().buildAnalyticsResponse(
                program, userRepository, campaignEventRepository, enrollmentRepository, dayCompletionRepository);
    }

    @Override
    @Transactional(readOnly = true)
    public long countEligibleAthletes(TargetAudience targetAudience) {
        return helper().countEligibleAthletes(targetAudience, userRepository);
    }

    @Override
    public void recordCampaignEvent(Long userId, GettingStartedCampaignEventRequest request) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required");
        }
        if (request == null || request.getEventType() == null) {
            throw new IllegalArgumentException("Event type is required");
        }
        if (campaignEventRepository == null) {
            return;
        }
        GettingStartedProgram program = (request.getProgramId() != null)
                ? programRepository.findById(request.getProgramId())
                        .filter(GettingStartedProgram::isPublished)
                        .orElseThrow(() -> new IllegalStateException("No published Getting Started program found"))
                : programRepository.findFirstPublished()
                        .orElseThrow(() -> new IllegalStateException("No published Getting Started program found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (!isUserEligibleForProgram(user, program)) {
            return;
        }
        campaignEventRepository.save(GettingStartedCampaignEvent.builder()
                .program(program)
                .programVersion(program.getVersion())
                .user(user)
                .eventType(request.getEventType())
                .build());
    }

    private void recordEventIfAbsent(
            GettingStartedProgram program,
            User user,
            GettingStartedCampaignEventType eventType
    ) {
        if (campaignEventRepository == null || program == null || user == null) {
            return;
        }
        boolean alreadyRecorded = campaignEventRepository.existsByProgramIdAndProgramVersionAndUserIdAndEventType(
                program.getId(), program.getVersion(), user.getId(), eventType);
        if (!alreadyRecorded) {
            campaignEventRepository.save(GettingStartedCampaignEvent.builder()
                    .program(program)
                    .programVersion(program.getVersion())
                    .user(user)
                    .eventType(eventType)
                    .build());
        }
    }

    private GettingStartedProgram resolveAdminTargetProgram(Long programId, boolean preferDraftWhenNull) {
        if (programId != null) {
            return programRepository.findById(programId)
                    .orElseThrow(() -> new IllegalArgumentException("Getting Started program not found: " + programId));
        }
        if (preferDraftWhenNull) {
            Optional<GettingStartedProgram> draft = programRepository.findLatestDraft();
            if (draft != null && draft.isPresent()) {
                return draft.get();
            }
        }
        return programRepository.findLatest()
                .orElseThrow(() -> new IllegalStateException("No Getting Started program found"));
    }

    private void validateProgramStrictly(AdminGettingStartedProgramRequest request) {
        List<String> errors = helper().collectValidationErrors(request);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(String.join(" ", errors));
        }
        validateProgramRequest(request);
    }

    private GettingStartedCampaignHelper helper() {
        return campaignHelper != null ? campaignHelper : new GettingStartedCampaignHelper();
    }
}
