package com.calisthenics.backend.analytics.service;

import com.calisthenics.backend.analytics.dto.CreatePersonalRecordRequest;
import com.calisthenics.backend.analytics.dto.PersonalRecordResponse;
import com.calisthenics.backend.analytics.dto.SharedPersonalRecordResponse;
import com.calisthenics.backend.analytics.dto.UpdatePersonalRecordRequest;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.model.SharedPersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.repository.SharedPersonalRecordRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonalRecordService {

    private final PersonalRecordRepository personalRecordRepository;
    private final SharedPersonalRecordRepository sharedPersonalRecordRepository;
    private final CoachClientRelationshipRepository coachClientRelationshipRepository;
    private final ExerciseRepository exerciseRepository;
    private final UserRepository userRepository;

    @Transactional
    public PersonalRecordResponse createPR(Long userId, CreatePersonalRecordRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Exercise exercise = null;
        if (request.getExerciseId() != null) {
            exercise = exerciseRepository.findById(request.getExerciseId()).orElse(null);
        }
        if (exercise == null && request.getExerciseName() != null && !request.getExerciseName().isBlank()) {
            exercise = exerciseRepository.findByNameIgnoreCase(request.getExerciseName().trim()).orElse(null);
        }

        String customExerciseName = null;
        if (exercise == null) {
            if (request.getExerciseName() == null || request.getExerciseName().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Exercise ID or exercise name is required");
            }
            customExerciseName = request.getExerciseName().trim();
        }

        PersonalRecordType type = request.getPrType();
        BigDecimal value = request.getValue();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PR value must be greater than zero");
        }

        String unit = (request.getUnit() != null && !request.getUnit().isBlank())
                ? request.getUnit().trim()
                : type.getDefaultUnit();

        ZonedDateTime achievedAt = request.getAchievedAt() != null ? request.getAchievedAt() : ZonedDateTime.now();
        BigDecimal normalizedValue = value.setScale(2, RoundingMode.HALF_UP);
        ZonedDateTime windowStart = achievedAt.minusSeconds(2);
        ZonedDateTime windowEnd = achievedAt.plusSeconds(2);

        // Prevent duplicate records for the same user, exercise, record type, value, and date
        boolean duplicate = false;
        if (exercise != null) {
            duplicate = personalRecordRepository.existsSimilarPr(
                    user.getId(), exercise.getId(), type, normalizedValue, windowStart, windowEnd);
        } else if (customExerciseName != null) {
            duplicate = personalRecordRepository.existsSimilarCustomPr(
                    user.getId(), customExerciseName, type, normalizedValue, windowStart, windowEnd);
        }
        if (duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A personal record with this value and date already exists for this exercise");
        }

        // Calculate improvement if previous best exists
        BigDecimal prevValue = null;
        BigDecimal improvement = null;
        if (exercise != null) {
            Optional<PersonalRecord> prevBest = personalRecordRepository.findTopByUserIdAndExerciseIdAndRecordTypeOrderByRecordValueDesc(
                    user.getId(), exercise.getId(), type);
            if (prevBest.isPresent() && prevBest.get().getRecordValue() != null) {
                prevValue = prevBest.get().getRecordValue();
                if (prevValue.compareTo(BigDecimal.ZERO) > 0) {
                    improvement = value.subtract(prevValue)
                            .divide(prevValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);
                }
            }
        }

        PersonalRecord record = PersonalRecord.builder()
                .user(user)
                .exercise(exercise)
                .customExerciseName(customExerciseName)
                .recordType(type)
                .recordValue(value.setScale(2, RoundingMode.HALF_UP))
                .unit(unit)
                .notes(request.getNotes() != null ? request.getNotes().trim() : null)
                .previousRecordValue(prevValue)
                .improvementPercentage(improvement)
                .achievedAt(achievedAt)
                .build();

        PersonalRecord saved = personalRecordRepository.save(record);
        log.info("User {} created PR for {} ({}: {} {})", userId, record.getEffectiveExerciseName(), type, value, unit);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordResponse> getUserPRsFiltered(
            Long userId,
            Long exerciseId,
            PersonalRecordType prType,
            ZonedDateTime startDate,
            ZonedDateTime endDate) {

        List<PersonalRecord> list = personalRecordRepository.findByUserIdAndFilters(userId, exerciseId, prType);

        if (startDate != null) {
            list = list.stream()
                    .filter(pr -> pr.getAchievedAt() != null && (pr.getAchievedAt().isAfter(startDate) || pr.getAchievedAt().isEqual(startDate)))
                    .toList();
        }
        if (endDate != null) {
            list = list.stream()
                    .filter(pr -> pr.getAchievedAt() != null && (pr.getAchievedAt().isBefore(endDate) || pr.getAchievedAt().isEqual(endDate)))
                    .toList();
        }

        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordResponse> getPRHistory(Long userId, Long exerciseId, PersonalRecordType prType) {
        return personalRecordRepository.findHistoryByUserIdAndExerciseId(userId, exerciseId, prType).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PersonalRecordResponse getLatestPR(Long userId, Long exerciseId, PersonalRecordType prType) {
        return personalRecordRepository.findLatestByUserIdAndExerciseId(userId, exerciseId, prType)
                .map(this::mapToResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No personal record found for this exercise"));
    }

    @Transactional
    public PersonalRecordResponse updatePR(Long userId, Long prId, UpdatePersonalRecordRequest request) {
        PersonalRecord pr = personalRecordRepository.findById(prId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal record not found"));

        if (!pr.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot update another user's personal record");
        }

        if (request.getPrType() != null) {
            pr.setRecordType(request.getPrType());
        }
        if (request.getValue() != null) {
            if (request.getValue().compareTo(BigDecimal.ZERO) <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PR value must be greater than zero");
            }
            pr.setRecordValue(request.getValue().setScale(2, RoundingMode.HALF_UP));
        }
        if (request.getUnit() != null) {
            pr.setUnit(request.getUnit().trim());
        }
        if (request.getAchievedAt() != null) {
            pr.setAchievedAt(request.getAchievedAt());
        }
        if (request.getNotes() != null) {
            pr.setNotes(request.getNotes().trim());
        }

        PersonalRecord updated = personalRecordRepository.save(pr);
        log.info("User {} updated PR {}", userId, prId);
        return mapToResponse(updated);
    }

    @Transactional
    public void deletePR(Long userId, Long prId) {
        PersonalRecord pr = personalRecordRepository.findById(prId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal record not found"));

        if (!pr.getUser().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot delete another user's personal record");
        }

        personalRecordRepository.delete(pr);
        log.info("User {} deleted PR {}", userId, prId);
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordResponse> getCoachClientPrRecords(
            Long coachId,
            Long clientId,
            Long exerciseId,
            PersonalRecordType prType) {

        coachClientRelationshipRepository.findActiveRelationshipForUsers(coachId, clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You are not authorized to view this client's records"));

        return personalRecordRepository.findByUserIdAndFilters(clientId, exerciseId, prType).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public List<PersonalRecordResponse> detectAndSavePRs(WorkoutSession session) {
        if (session == null || session.getStatus() != WorkoutSessionStatus.COMPLETED) {
            return Collections.emptyList();
        }

        User user = session.getUser();
        ZonedDateTime sessionDate = session.getCompletedAt() != null ? session.getCompletedAt() : ZonedDateTime.now();
        List<PersonalRecord> newlyCreatedPRs = new ArrayList<>();

        for (WorkoutSessionExercise exerciseItem : session.getExercises()) {
            Exercise exercise = exerciseItem.getExercise();
            if (exercise == null) {
                continue;
            }

            List<WorkoutSessionSet> completedSets = exerciseItem.getSets().stream()
                    .filter(WorkoutSessionSet::isCompleted)
                    .toList();

            if (completedSets.isEmpty()) {
                continue;
            }

            // 1. MAX WEIGHT PR
            WorkoutSessionSet bestWeightSet = null;
            for (WorkoutSessionSet s : completedSets) {
                if (s.getWeightKg() != null && s.getWeightKg().compareTo(BigDecimal.ZERO) > 0) {
                    if (bestWeightSet == null || s.getWeightKg().compareTo(bestWeightSet.getWeightKg()) > 0 ||
                            (s.getWeightKg().compareTo(bestWeightSet.getWeightKg()) == 0 &&
                                    (s.getReps() != null && (bestWeightSet.getReps() == null || s.getReps() > bestWeightSet.getReps())))) {
                        bestWeightSet = s;
                    }
                }
            }

            if (bestWeightSet != null && !personalRecordRepository.existsByUserIdAndWorkoutSessionIdAndExerciseIdAndRecordType(
                    user.getId(), session.getId(), exercise.getId(), PersonalRecordType.MAX_WEIGHT)) {

                BigDecimal currentWeight = bestWeightSet.getWeightKg();
                BigDecimal currentReps = bestWeightSet.getReps() != null ? BigDecimal.valueOf(bestWeightSet.getReps()) : null;

                Optional<PersonalRecord> prevWeightPr = personalRecordRepository.findBestHistoricalRecord(
                        user.getId(), exercise.getId(), PersonalRecordType.MAX_WEIGHT, sessionDate);

                if (prevWeightPr.isEmpty() || currentWeight.compareTo(prevWeightPr.get().getRecordValue()) > 0) {
                    BigDecimal prevVal = prevWeightPr.map(PersonalRecord::getRecordValue).orElse(null);
                    BigDecimal prevSec = prevWeightPr.map(PersonalRecord::getSecondaryValue).orElse(null);
                    BigDecimal improvement = null;
                    if (prevVal != null && prevVal.compareTo(BigDecimal.ZERO) > 0) {
                        improvement = currentWeight.subtract(prevVal)
                                .divide(prevVal, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    PersonalRecord pr = PersonalRecord.builder()
                            .user(user)
                            .exercise(exercise)
                            .workoutSession(session)
                            .workoutSessionSet(bestWeightSet)
                            .recordType(PersonalRecordType.MAX_WEIGHT)
                            .recordValue(currentWeight)
                            .unit("kg")
                            .secondaryValue(currentReps)
                            .previousRecordValue(prevVal)
                            .previousSecondaryValue(prevSec)
                            .improvementPercentage(improvement)
                            .achievedAt(sessionDate)
                            .build();

                    newlyCreatedPRs.add(personalRecordRepository.save(pr));
                }
            }

            // 2. MAX REPS PR
            WorkoutSessionSet bestRepsSet = null;
            for (WorkoutSessionSet s : completedSets) {
                if (s.getReps() != null && s.getReps() > 0) {
                    if (bestRepsSet == null || s.getReps() > bestRepsSet.getReps()) {
                        bestRepsSet = s;
                    }
                }
            }

            if (bestRepsSet != null && !personalRecordRepository.existsByUserIdAndWorkoutSessionIdAndExerciseIdAndRecordType(
                    user.getId(), session.getId(), exercise.getId(), PersonalRecordType.MAX_REPS)) {

                BigDecimal currentReps = BigDecimal.valueOf(bestRepsSet.getReps());
                BigDecimal currentWeight = bestRepsSet.getWeightKg();

                Optional<PersonalRecord> prevRepsPr = personalRecordRepository.findBestHistoricalRecord(
                        user.getId(), exercise.getId(), PersonalRecordType.MAX_REPS, sessionDate);

                if (prevRepsPr.isEmpty() || currentReps.compareTo(prevRepsPr.get().getRecordValue()) > 0) {
                    BigDecimal prevVal = prevRepsPr.map(PersonalRecord::getRecordValue).orElse(null);
                    BigDecimal prevSec = prevRepsPr.map(PersonalRecord::getSecondaryValue).orElse(null);
                    BigDecimal improvement = null;
                    if (prevVal != null && prevVal.compareTo(BigDecimal.ZERO) > 0) {
                        improvement = currentReps.subtract(prevVal)
                                .divide(prevVal, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    PersonalRecord pr = PersonalRecord.builder()
                            .user(user)
                            .exercise(exercise)
                            .workoutSession(session)
                            .workoutSessionSet(bestRepsSet)
                            .recordType(PersonalRecordType.MAX_REPS)
                            .recordValue(currentReps)
                            .unit("reps")
                            .secondaryValue(currentWeight)
                            .previousRecordValue(prevVal)
                            .previousSecondaryValue(prevSec)
                            .improvementPercentage(improvement)
                            .achievedAt(sessionDate)
                            .build();

                    newlyCreatedPRs.add(personalRecordRepository.save(pr));
                }
            }

            // 3. MAX DURATION / HOLD PR
            WorkoutSessionSet bestDurationSet = null;
            for (WorkoutSessionSet s : completedSets) {
                if (s.getHoldDurationSeconds() != null && s.getHoldDurationSeconds() > 0) {
                    if (bestDurationSet == null || s.getHoldDurationSeconds() > bestDurationSet.getHoldDurationSeconds()) {
                        bestDurationSet = s;
                    }
                }
            }

            if (bestDurationSet != null && !personalRecordRepository.existsByUserIdAndWorkoutSessionIdAndExerciseIdAndRecordType(
                    user.getId(), session.getId(), exercise.getId(), PersonalRecordType.MAX_DURATION)) {

                BigDecimal currentDur = BigDecimal.valueOf(bestDurationSet.getHoldDurationSeconds());

                Optional<PersonalRecord> prevDurPr = personalRecordRepository.findBestHistoricalRecord(
                        user.getId(), exercise.getId(), PersonalRecordType.MAX_DURATION, sessionDate);

                if (prevDurPr.isEmpty() || currentDur.compareTo(prevDurPr.get().getRecordValue()) > 0) {
                    BigDecimal prevVal = prevDurPr.map(PersonalRecord::getRecordValue).orElse(null);
                    BigDecimal improvement = null;
                    if (prevVal != null && prevVal.compareTo(BigDecimal.ZERO) > 0) {
                        improvement = currentDur.subtract(prevVal)
                                .divide(prevVal, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    PersonalRecord pr = PersonalRecord.builder()
                            .user(user)
                            .exercise(exercise)
                            .workoutSession(session)
                            .workoutSessionSet(bestDurationSet)
                            .recordType(PersonalRecordType.MAX_DURATION)
                            .recordValue(currentDur)
                            .unit("seconds")
                            .previousRecordValue(prevVal)
                            .improvementPercentage(improvement)
                            .achievedAt(sessionDate)
                            .build();

                    newlyCreatedPRs.add(personalRecordRepository.save(pr));
                }
            }

            // 4. MAX VOLUME PR (for weighted exercises)
            BigDecimal sessionVolume = BigDecimal.ZERO;
            boolean hasWeighted = false;
            for (WorkoutSessionSet s : completedSets) {
                if (s.getWeightKg() != null && s.getWeightKg().compareTo(BigDecimal.ZERO) > 0 && s.getReps() != null) {
                    sessionVolume = sessionVolume.add(s.getWeightKg().multiply(BigDecimal.valueOf(s.getReps())));
                    hasWeighted = true;
                }
            }

            if (hasWeighted && sessionVolume.compareTo(BigDecimal.ZERO) > 0 &&
                    !personalRecordRepository.existsByUserIdAndWorkoutSessionIdAndExerciseIdAndRecordType(
                            user.getId(), session.getId(), exercise.getId(), PersonalRecordType.MAX_VOLUME)) {

                Optional<PersonalRecord> prevVolPr = personalRecordRepository.findBestHistoricalRecord(
                        user.getId(), exercise.getId(), PersonalRecordType.MAX_VOLUME, sessionDate);

                if (prevVolPr.isEmpty() || sessionVolume.compareTo(prevVolPr.get().getRecordValue()) > 0) {
                    BigDecimal prevVal = prevVolPr.map(PersonalRecord::getRecordValue).orElse(null);
                    BigDecimal improvement = null;
                    if (prevVal != null && prevVal.compareTo(BigDecimal.ZERO) > 0) {
                        improvement = sessionVolume.subtract(prevVal)
                                .divide(prevVal, 4, RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .setScale(2, RoundingMode.HALF_UP);
                    }

                    PersonalRecord pr = PersonalRecord.builder()
                            .user(user)
                            .exercise(exercise)
                            .workoutSession(session)
                            .recordType(PersonalRecordType.MAX_VOLUME)
                            .recordValue(sessionVolume.setScale(2, RoundingMode.HALF_UP))
                            .unit("kg")
                            .previousRecordValue(prevVal)
                            .improvementPercentage(improvement)
                            .achievedAt(sessionDate)
                            .build();

                    newlyCreatedPRs.add(personalRecordRepository.save(pr));
                }
            }
        }

        return newlyCreatedPRs.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordResponse> getSessionPRs(Long sessionId) {
        return personalRecordRepository.findByWorkoutSessionId(sessionId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordResponse> getUserPRs(Long userId) {
        return personalRecordRepository.findByUserIdOrderByAchievedAtDesc(userId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PersonalRecordResponse> getExercisePRs(Long userId, Long exerciseId) {
        return personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedAtDesc(userId, exerciseId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public SharedPersonalRecordResponse sharePRWithCoach(Long clientId, Long prId) {
        PersonalRecord pr = personalRecordRepository.findById(prId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal record not found"));

        if (!pr.getUser().getId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot share a record belonging to another user");
        }

        CoachClientRelationship activeRelationship = coachClientRelationshipRepository
                .findFirstByClientIdAndStatusOrderByUpdatedAtDesc(clientId, CoachClientRelationshipStatus.ACTIVE)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "You do not have an active coach assigned to share this PR with."));

        User coach = activeRelationship.getCoach();

        if (sharedPersonalRecordRepository.existsByPersonalRecordIdAndCoachId(prId, coach.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This PR has already been shared with your coach");
        }

        SharedPersonalRecord shared = SharedPersonalRecord.builder()
                .personalRecord(pr)
                .client(pr.getUser())
                .coach(coach)
                .status("SHARED")
                .build();

        SharedPersonalRecord saved = sharedPersonalRecordRepository.save(shared);
        log.info("Client {} shared PR {} with Coach {}", clientId, prId, coach.getId());
        return mapToSharedResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<SharedPersonalRecordResponse> getClientSharedPRs(Long clientId) {
        return sharedPersonalRecordRepository.findByClientIdOrderBySharedAtDesc(clientId).stream()
                .map(this::mapToSharedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SharedPersonalRecordResponse> getCoachClientPRs(Long coachId, Long clientId) {
        coachClientRelationshipRepository.findActiveRelationshipForUsers(coachId, clientId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "You are not authorized to view this client's records"));

        return sharedPersonalRecordRepository.findByCoachIdAndClientIdOrderBySharedAtDesc(coachId, clientId).stream()
                .map(this::mapToSharedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SharedPersonalRecordResponse> getCoachAllSharedPRs(Long coachId) {
        return sharedPersonalRecordRepository.findByCoachIdOrderBySharedAtDesc(coachId).stream()
                .map(this::mapToSharedResponse)
                .toList();
    }

    @Transactional
    public SharedPersonalRecordResponse addCoachFeedback(Long coachId, Long shareId, String feedback) {
        SharedPersonalRecord share = sharedPersonalRecordRepository.findByIdAndCoachId(shareId, coachId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shared PR not found or you are not authorized to provide feedback"));

        share.setCoachFeedback(feedback != null ? feedback.trim() : null);
        share.setFeedbackAt(ZonedDateTime.now());
        SharedPersonalRecord updated = sharedPersonalRecordRepository.save(share);
        return mapToSharedResponse(updated);
    }

    public PersonalRecordResponse mapToResponse(PersonalRecord pr) {
        String unit = pr.getUnit() != null ? pr.getUnit() : pr.getRecordType().getDefaultUnit();
        String formatted = formatRecord(pr.getRecordType(), pr.getRecordValue(), pr.getSecondaryValue(), unit);
        String formattedPrev = pr.getPreviousRecordValue() != null
                ? formatRecord(pr.getRecordType(), pr.getPreviousRecordValue(), pr.getPreviousSecondaryValue(), unit)
                : null;

        return PersonalRecordResponse.builder()
                .id(pr.getId())
                .exerciseId(pr.getExercise() != null ? pr.getExercise().getId() : null)
                .exerciseName(pr.getEffectiveExerciseName())
                .sessionId(pr.getWorkoutSession() != null ? pr.getWorkoutSession().getId() : null)
                .recordType(pr.getRecordType().name())
                .recordValue(pr.getRecordValue())
                .unit(unit)
                .notes(pr.getNotes())
                .secondaryValue(pr.getSecondaryValue())
                .previousRecordValue(pr.getPreviousRecordValue())
                .previousSecondaryValue(pr.getPreviousSecondaryValue())
                .improvementPercentage(pr.getImprovementPercentage())
                .achievedAt(pr.getAchievedAt())
                .createdAt(pr.getCreatedAt())
                .updatedAt(pr.getUpdatedAt())
                .formattedRecord(formatted)
                .formattedPrevious(formattedPrev)
                .build();
    }

    public SharedPersonalRecordResponse mapToSharedResponse(SharedPersonalRecord shared) {
        PersonalRecordResponse prDto = mapToResponse(shared.getPersonalRecord());
        return SharedPersonalRecordResponse.builder()
                .id(shared.getId())
                .personalRecord(prDto)
                .clientId(shared.getClient().getId())
                .clientName(shared.getClient().getFullName())
                .clientAvatarUrl(shared.getClient().getAvatarUrl())
                .coachId(shared.getCoach().getId())
                .coachName(shared.getCoach().getFullName())
                .sharedAt(shared.getSharedAt())
                .coachFeedback(shared.getCoachFeedback())
                .feedbackAt(shared.getFeedbackAt())
                .status(shared.getStatus())
                .build();
    }

    private String formatRecord(PersonalRecordType type, BigDecimal value, BigDecimal secondary, String unit) {
        if (value == null) return "";
        String valStr = value.stripTrailingZeros().toPlainString();
        return switch (type) {
            case MAX_WEIGHT -> {
                String weightStr = "+" + valStr + " " + (unit != null ? unit : "kg");
                if (secondary != null && secondary.compareTo(BigDecimal.ZERO) > 0) {
                    yield weightStr + " × " + secondary.intValue() + " reps";
                }
                yield weightStr;
            }
            case MAX_REPS, BODYWEIGHT -> {
                String repsStr = valStr + " " + (unit != null ? unit : "reps");
                if (secondary != null && secondary.compareTo(BigDecimal.ZERO) > 0) {
                    yield repsStr + " @ +" + secondary.stripTrailingZeros().toPlainString() + " kg";
                }
                yield repsStr;
            }
            case MAX_HOLD -> valStr + " " + (unit != null ? unit : "seconds") + " hold";
            case MAX_DURATION -> (unit != null && !unit.equalsIgnoreCase("seconds") ? valStr + " " + unit : valStr + "s hold");
            case MAX_DISTANCE -> valStr + " " + (unit != null ? unit : "km");
            case MAX_VOLUME -> valStr + " " + (unit != null ? unit : "kg") + " volume";
        };
    }
}
