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
import com.calisthenics.backend.chat.dto.response.ChatMessageResponse;
import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.entity.ChatMessage;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.chat.repository.ChatMessageRepository;
import com.calisthenics.backend.chat.type.ChatMessageType;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.notification.model.NotificationType;
import com.calisthenics.backend.notification.service.NotificationService;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionExercise;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
    private final NotificationService notificationService;

    @Autowired(required = false)
    private ChatConversationRepository chatConversationRepository;

    @Autowired(required = false)
    private ChatMessageRepository chatMessageRepository;

    @Autowired(required = false)
    private SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

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
    public List<SharedPersonalRecordResponse> sharePRsWithCoach(Long clientId, List<Long> prIds) {
        return sharePRsWithCoach(clientId, prIds, null, null);
    }

    @Transactional
    public List<SharedPersonalRecordResponse> sharePRsWithCoach(
            Long clientId,
            List<Long> prIds,
            Long recipientId,
            String clientMessageId) {
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        if (prIds == null || prIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one PR ID must be provided");
        }

        List<PersonalRecord> prsToShare = new ArrayList<>();
        for (Long prId : prIds) {
            PersonalRecord pr = personalRecordRepository.findById(prId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal record not found: " + prId));

            if (!pr.getUser().getId().equals(clientId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot share a record belonging to another user");
            }
            prsToShare.add(pr);
        }

        CoachClientRelationship activeRelationship = resolveActiveRelationshipForSharing(clientId, recipientId);
        User recipient = resolveRecipientFromRelationship(clientId, activeRelationship);

        List<SharedPersonalRecordResponse> responses = new ArrayList<>();
        int alreadySharedCount = 0;

        for (int index = 0; index < prsToShare.size(); index++) {
            PersonalRecord pr = prsToShare.get(index);
            String perPrClientMessageId = null;
            if (clientMessageId != null && !clientMessageId.isBlank()) {
                String trimmed = clientMessageId.trim();
                perPrClientMessageId = prsToShare.size() == 1 ? trimmed : trimmed + "-" + pr.getId();
                if (perPrClientMessageId.length() > 64) {
                    perPrClientMessageId = perPrClientMessageId.substring(0, 64);
                }
            }

            Optional<SharedPersonalRecord> existing = sharedPersonalRecordRepository
                    .findByPersonalRecordIdAndCoachId(pr.getId(), recipient.getId());
            if (existing.isPresent()) {
                alreadySharedCount++;
                SharedPersonalRecord existingShare = existing.get();
                SharedPersonalRecordResponse existingResponse = mapToSharedResponse(existingShare);
                attachExistingOrIdempotentChatMessage(
                        activeRelationship, pr.getUser(), recipient, existingShare, existingResponse, perPrClientMessageId);
                responses.add(existingResponse);
                continue;
            }

            SharedPersonalRecord shared = SharedPersonalRecord.builder()
                    .personalRecord(pr)
                    .client(pr.getUser())
                    .coach(recipient)
                    .status("SHARED")
                    .isRead(false)
                    .build();

            SharedPersonalRecord saved = sharedPersonalRecordRepository.save(shared);

            // Notify Recipient
            String unit = pr.getUnit() != null ? pr.getUnit() : pr.getRecordType().getDefaultUnit();
            String formattedPr = formatRecord(pr.getRecordType(), pr.getRecordValue(), pr.getSecondaryValue(), unit);
            notificationService.createNotification(
                    recipient,
                    pr.getUser(),
                    NotificationType.PR_SHARED,
                    saved.getId(),
                    "New PR Shared",
                    pr.getUser().getFullName() + " shared a new PR: " + pr.getEffectiveExerciseName() + " (" + formattedPr + ")"
            );
            log.info("User {} shared PR {} with Recipient {}", clientId, pr.getId(), recipient.getId());

            SharedPersonalRecordResponse sharedResponse = mapToSharedResponse(saved);
            sendSharedPrChatMessage(
                    activeRelationship, pr.getUser(), recipient, saved, sharedResponse, perPrClientMessageId);
            responses.add(sharedResponse);
        }

        if (alreadySharedCount == prsToShare.size() && prsToShare.size() == 1) {
            // If this is an idempotent retry with a matching clientMessageId, return the existing share instead of throwing 409
            if (clientMessageId != null && !clientMessageId.isBlank()
                    && !responses.isEmpty()
                    && responses.getFirst().getChatMessageId() != null) {
                return responses;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This PR has already been shared with this recipient");
        }

        return responses;
    }

    @Transactional
    public SharedPersonalRecordResponse sharePRWithCoach(Long clientId, Long prId) {
        return sharePRWithCoach(clientId, prId, null, null);
    }

    @Transactional
    public SharedPersonalRecordResponse sharePRWithCoach(
            Long clientId,
            Long prId,
            Long recipientId,
            String clientMessageId) {
        if (clientId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        PersonalRecord pr = personalRecordRepository.findById(prId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Personal record not found"));

        if (!pr.getUser().getId().equals(clientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot share a record belonging to another user");
        }

        CoachClientRelationship activeRelationship = resolveActiveRelationshipForSharing(clientId, recipientId);
        User recipient = resolveRecipientFromRelationship(clientId, activeRelationship);

        String trimmedClientMessageId = (clientMessageId != null && !clientMessageId.isBlank())
                ? clientMessageId.trim()
                : null;

        Optional<SharedPersonalRecord> existingOpt = sharedPersonalRecordRepository
                .findByPersonalRecordIdAndCoachId(prId, recipient.getId());
        if (existingOpt.isPresent()) {
            // If this is an idempotent retry with a clientMessageId that matches an existing chat message, return 200 OK
            if (trimmedClientMessageId != null) {
                SharedPersonalRecord existingShare = existingOpt.get();
                SharedPersonalRecordResponse existingResponse = mapToSharedResponse(existingShare);
                if (attachExistingOrIdempotentChatMessage(
                        activeRelationship, pr.getUser(), recipient, existingShare, existingResponse, trimmedClientMessageId)) {
                    return existingResponse;
                }
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This PR has already been shared with this recipient");
        }
        if (sharedPersonalRecordRepository.existsByPersonalRecordIdAndCoachId(prId, recipient.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This PR has already been shared with this recipient");
        }

        SharedPersonalRecord shared = SharedPersonalRecord.builder()
                .personalRecord(pr)
                .client(pr.getUser())
                .coach(recipient)
                .status("SHARED")
                .isRead(false)
                .build();

        SharedPersonalRecord saved = sharedPersonalRecordRepository.save(shared);

        // Notify Recipient
        String unit = pr.getUnit() != null ? pr.getUnit() : pr.getRecordType().getDefaultUnit();
        String formattedPr = formatRecord(pr.getRecordType(), pr.getRecordValue(), pr.getSecondaryValue(), unit);
        notificationService.createNotification(
                recipient,
                pr.getUser(),
                NotificationType.PR_SHARED,
                saved.getId(),
                "New PR Shared",
                pr.getUser().getFullName() + " shared a new PR: " + pr.getEffectiveExerciseName() + " (" + formattedPr + ")"
        );
        log.info("User {} shared PR {} with Recipient {}", clientId, prId, recipient.getId());

        SharedPersonalRecordResponse response = mapToSharedResponse(saved);
        sendSharedPrChatMessage(activeRelationship, pr.getUser(), recipient, saved, response, trimmedClientMessageId);
        return response;
    }

    private CoachClientRelationship resolveActiveRelationshipForSharing(Long senderId, Long recipientId) {
        if (recipientId != null) {
            if (senderId.equals(recipientId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot share a Personal Record with yourself");
            }
            return coachClientRelationshipRepository
                    .findActiveRelationshipForUsers(senderId, recipientId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "You do not have an active coach-client relationship with the selected recipient."));
        }

        return coachClientRelationshipRepository
                .findFirstByClientIdAndStatusOrderByUpdatedAtDesc(senderId, CoachClientRelationshipStatus.ACTIVE)
                .orElseGet(() -> coachClientRelationshipRepository
                        .findByCoachIdOrClientIdOrderByCreatedAtDesc(senderId, senderId).stream()
                        .filter(rel -> rel.getStatus() == CoachClientRelationshipStatus.ACTIVE)
                        .findFirst()
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "You do not have an active coach or client connection to share this PR with.")));
    }

    private User resolveRecipientFromRelationship(Long senderId, CoachClientRelationship relationship) {
        if (relationship.getClient() != null && senderId.equals(relationship.getClient().getId())) {
            return relationship.getCoach();
        }
        return relationship.getClient();
    }

    private boolean attachExistingOrIdempotentChatMessage(
            CoachClientRelationship relationship,
            User sender,
            User recipient,
            SharedPersonalRecord shared,
            SharedPersonalRecordResponse response,
            String clientMessageId) {
        if (chatConversationRepository == null || chatMessageRepository == null || clientMessageId == null) {
            return false;
        }
        Long coachId = relationship.getCoach().getId();
        Long clientId = relationship.getClient().getId();
        Optional<ChatConversation> convOpt = chatConversationRepository.findByCoachIdAndClientId(coachId, clientId);
        if (convOpt.isEmpty()) {
            return false;
        }
        ChatConversation conversation = convOpt.get();
        Optional<ChatMessage> existingMsg = chatMessageRepository
                .findByConversationIdAndClientMessageId(conversation.getId(), clientMessageId);
        if (existingMsg.isPresent()) {
            response.setConversationId(conversation.getId());
            response.setChatMessageId(existingMsg.get().getId());
            return true;
        }
        return false;
    }

    private void sendSharedPrChatMessage(
            CoachClientRelationship relationship,
            User sender,
            User recipient,
            SharedPersonalRecord savedShare,
            SharedPersonalRecordResponse shareResponse,
            String clientMessageId) {
        if (chatConversationRepository == null || chatMessageRepository == null) {
            return;
        }
        try {
            Long coachId = relationship.getCoach().getId();
            Long clientId = relationship.getClient().getId();
            ZonedDateTime now = ZonedDateTime.now();

            ChatConversation conversation = chatConversationRepository.findByCoachIdAndClientId(coachId, clientId)
                    .orElseGet(() -> chatConversationRepository.save(ChatConversation.builder()
                            .coach(relationship.getCoach())
                            .client(relationship.getClient())
                            .coachLastReadAt(sender.getId().equals(coachId) ? now : null)
                            .clientLastReadAt(sender.getId().equals(clientId) ? now : null)
                            .build()));

            String effectiveClientMsgId = (clientMessageId != null && !clientMessageId.isBlank())
                    ? clientMessageId.trim()
                    : "pr-share-" + savedShare.getId();

            Optional<ChatMessage> existingMessage = chatMessageRepository
                    .findByConversationIdAndClientMessageId(conversation.getId(), effectiveClientMsgId);
            if (existingMessage.isPresent()) {
                shareResponse.setConversationId(conversation.getId());
                shareResponse.setChatMessageId(existingMessage.get().getId());
                return;
            }

            shareResponse.setConversationId(conversation.getId());

            ObjectMapper mapper = objectMapper != null
                    ? objectMapper
                    : new ObjectMapper().registerModule(new JavaTimeModule());
            String contentJson = mapper.writeValueAsString(shareResponse);

            ChatMessage savedMessage = chatMessageRepository.save(ChatMessage.builder()
                    .conversation(conversation)
                    .sender(sender)
                    .content(contentJson)
                    .clientMessageId(effectiveClientMsgId)
                    .messageType(ChatMessageType.PR_SHARE)
                    .build());

            ZonedDateTime msgTime = savedMessage.getCreatedAt() != null ? savedMessage.getCreatedAt() : now;
            conversation.setLastMessageAt(msgTime);
            if (sender.getId().equals(coachId)) {
                conversation.setCoachLastReadAt(msgTime);
            } else if (sender.getId().equals(clientId)) {
                conversation.setClientLastReadAt(msgTime);
            }
            chatConversationRepository.save(conversation);

            shareResponse.setChatMessageId(savedMessage.getId());

            if (messagingTemplate != null) {
                ChatMessageResponse chatMessageResponse = ChatMessageResponse.builder()
                        .id(savedMessage.getId())
                        .conversationId(conversation.getId())
                        .senderId(sender.getId())
                        .senderName(sender.getFullName())
                        .senderAvatarUrl(sender.getAvatarUrl())
                        .content(savedMessage.getContent())
                        .clientMessageId(savedMessage.getClientMessageId())
                        .messageType(savedMessage.getMessageType())
                        .sharedPersonalRecord(shareResponse)
                        .createdAt(msgTime)
                        .build();

                messagingTemplate.convertAndSend("/topic/conversations/" + conversation.getId(), chatMessageResponse);
                messagingTemplate.convertAndSendToUser(String.valueOf(recipient.getId()), "/queue/messages", chatMessageResponse);
            }
        } catch (Exception e) {
            log.warn("Failed to send chat message for shared PR {}: {}", savedShare.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<SharedPersonalRecordResponse> getClientSharedPRs(Long clientId) {
        return sharedPersonalRecordRepository.findByClientIdOrderBySharedAtDesc(clientId).stream()
                .map(this::mapToSharedResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SharedPersonalRecordResponse> getClientFeedbacks(Long clientId) {
        return sharedPersonalRecordRepository.findByClientIdAndCoachFeedbackIsNotNullOrderByFeedbackAtDesc(clientId).stream()
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
        return getCoachAllSharedPRs(coachId, null);
    }

    @Transactional(readOnly = true)
    public List<SharedPersonalRecordResponse> getCoachAllSharedPRs(Long coachId, Boolean unread) {
        List<SharedPersonalRecord> list = Boolean.TRUE.equals(unread)
                ? sharedPersonalRecordRepository.findByCoachIdAndIsReadOrderBySharedAtDesc(coachId, false)
                : sharedPersonalRecordRepository.findByCoachIdOrderBySharedAtDesc(coachId);

        return list.stream()
                .map(this::mapToSharedResponse)
                .toList();
    }

    @Transactional
    public SharedPersonalRecordResponse markShareAsRead(Long coachId, Long shareId) {
        SharedPersonalRecord share = sharedPersonalRecordRepository.findByIdAndCoachId(shareId, coachId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shared PR not found or you are not authorized to view it"));

        if (!share.isRead()) {
            share.setRead(true);
            share.setReadAt(ZonedDateTime.now());
            share = sharedPersonalRecordRepository.save(share);
        }

        return mapToSharedResponse(share);
    }

    @Transactional
    public SharedPersonalRecordResponse addCoachFeedback(Long coachId, Long shareId, String feedback) {
        return addCoachFeedback(coachId, shareId, feedback, null);
    }

    @Transactional
    public SharedPersonalRecordResponse addCoachFeedback(Long coachId, Long shareId, String feedback, Integer rating) {
        SharedPersonalRecord share = sharedPersonalRecordRepository.findByIdAndCoachId(shareId, coachId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Shared PR not found or you are not authorized to provide feedback"));

        share.setCoachFeedback(feedback != null ? feedback.trim() : null);
        share.setRating(rating);
        share.setFeedbackAt(ZonedDateTime.now());
        share.setStatus("REVIEWED");
        SharedPersonalRecord updated = sharedPersonalRecordRepository.save(share);

        // Notify client about coach feedback
        String exerciseName = share.getPersonalRecord() != null ? share.getPersonalRecord().getEffectiveExerciseName() : "PR";
        String feedbackPreview = feedback != null && !feedback.isBlank() ? ": \"" + feedback.trim() + "\"" : ".";
        notificationService.createNotification(
                share.getClient(),
                share.getCoach(),
                NotificationType.COACH_FEEDBACK,
                share.getId(),
                "Coach Feedback on PR",
                "Coach " + share.getCoach().getFullName() + " gave feedback on your " + exerciseName + " PR" + feedbackPreview
        );

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
                .rating(shared.getRating())
                .isRead(shared.isRead())
                .readAt(shared.getReadAt())
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
