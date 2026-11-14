package com.calisthenics.backend.gettingstarted.service.impl;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.gettingstarted.dto.*;
import com.calisthenics.backend.gettingstarted.entity.*;
import com.calisthenics.backend.gettingstarted.enums.*;
import com.calisthenics.backend.gettingstarted.repository.*;
import com.calisthenics.backend.gettingstarted.service.GettingStartedService;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        return mapToProgramResponse(saved, null, false, OnboardingStatus.DISMISSED);
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
        GettingStartedProgram program = programRepository.findFirstPublished()
                .orElseThrow(() -> new IllegalStateException("Getting Started program is not published"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        GettingStartedEnrollment existing = enrollmentRepository.findByUserIdAndProgramId(userId, program.getId()).orElse(null);
        if (existing != null) {
            return mapToEnrollmentResponse(existing);
        }

        GettingStartedEnrollment newEnrollment = GettingStartedEnrollment.builder()
                .user(user)
                .program(program)
                .programVersion(program.getVersion())
                .status(GettingStartedEnrollmentStatus.ACTIVE)
                .build();

        GettingStartedEnrollment saved = enrollmentRepository.save(newEnrollment);
        return mapToEnrollmentResponse(saved);
    }

    @Override
    public GettingStartedEnrollmentResponse completeDay(Long userId, Integer dayNumber) {
        if (dayNumber == null || dayNumber < 1 || dayNumber > 3) {
            throw new IllegalArgumentException("Day number must be 1, 2, or 3");
        }

        GettingStartedProgram program = programRepository.findFirstPublished()
                .orElseThrow(() -> new IllegalStateException("Getting Started program is not published"));

        GettingStartedEnrollment enrollment = enrollmentRepository.findByUserIdAndProgramId(userId, program.getId())
                .orElseThrow(() -> new IllegalStateException("Athlete is not enrolled in the Getting Started program"));

        Optional<GettingStartedDayCompletion> existingCompletion =
                dayCompletionRepository.findByEnrollmentIdAndDayNumber(enrollment.getId(), dayNumber);

        if (existingCompletion.isEmpty()) {
            GettingStartedDayCompletion completion = GettingStartedDayCompletion.builder()
                    .enrollment(enrollment)
                    .dayNumber(dayNumber)
                    .build();
            dayCompletionRepository.save(completion);
            enrollment.getCompletions().add(completion);
        }

        Set<Integer> completedDays = enrollment.getCompletions().stream()
                .map(GettingStartedDayCompletion::getDayNumber)
                .collect(Collectors.toSet());

        if (completedDays.contains(1) && completedDays.contains(2) && completedDays.contains(3)) {
            enrollment.setStatus(GettingStartedEnrollmentStatus.COMPLETED);
            enrollment.setCompletedAt(ZonedDateTime.now());
            enrollmentRepository.save(enrollment);
        }

        return mapToEnrollmentResponse(enrollment);
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
        Set<Integer> completedDays = (enrollment != null)
                ? enrollment.getCompletions().stream()
                    .map(GettingStartedDayCompletion::getDayNumber)
                    .collect(Collectors.toSet())
                : Collections.emptySet();

        List<GettingStartedDayResponse> dayResponses = program.getDays().stream()
                .sorted(Comparator.comparingInt(GettingStartedProgramDay::getDayNumber))
                .map(day -> GettingStartedDayResponse.builder()
                        .id(day.getId())
                        .dayNumber(day.getDayNumber())
                        .title(day.getTitle())
                        .description(day.getDescription())
                        .completed(completedDays.contains(day.getDayNumber()))
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
                        .build())
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
                .enrollment(enrollment != null ? mapToEnrollmentResponse(enrollment) : null)
                .build();
    }

    private GettingStartedEnrollmentResponse mapToEnrollmentResponse(GettingStartedEnrollment enrollment) {
        List<Integer> completedDays = enrollment.getCompletions().stream()
                .map(GettingStartedDayCompletion::getDayNumber)
                .sorted()
                .collect(Collectors.toList());

        return GettingStartedEnrollmentResponse.builder()
                .id(enrollment.getId())
                .programId(enrollment.getProgram().getId())
                .programVersion(enrollment.getProgramVersion())
                .status(enrollment.getStatus().name())
                .enrolledAt(enrollment.getEnrolledAt())
                .completedAt(enrollment.getCompletedAt())
                .completedDays(completedDays)
                .completedDaysCount(completedDays.size())
                .isCompleted(enrollment.getStatus() == GettingStartedEnrollmentStatus.COMPLETED)
                .build();
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
