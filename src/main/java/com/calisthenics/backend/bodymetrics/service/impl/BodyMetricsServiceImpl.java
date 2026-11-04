package com.calisthenics.backend.bodymetrics.service.impl;

import com.calisthenics.backend.bodymetrics.dto.*;
import com.calisthenics.backend.bodymetrics.entity.ClientBodyMeasurement;
import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
import com.calisthenics.backend.bodymetrics.service.BodyMetricsService;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class BodyMetricsServiceImpl implements BodyMetricsService {

    private final ClientBodyMeasurementRepository measurementRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final UserRepository userRepository;
    private final CoachClientRelationshipRepository relationshipRepository;

    private static BigDecimal toBigDecimal(Double val) {
        return val != null ? BigDecimal.valueOf(val) : null;
    }

    private static Double toDouble(BigDecimal val) {
        return val != null ? val.doubleValue() : null;
    }

    @Override
    @Transactional(readOnly = true)
    public CurrentBodyMetricsResponse getCurrentMetrics(Long clientId) {
        verifyUserExists(clientId);

        Double currentHeight = clientProfileRepository.findByUserId(clientId)
                .map(ClientProfile::getHeightCm)
                .map(BodyMetricsServiceImpl::toDouble)
                .orElse(null);

        Optional<ClientBodyMeasurement> latestOpt = measurementRepository
                .findFirstByClientIdOrderByMeasuredAtDesc(clientId);

        long count = measurementRepository.countByClientId(clientId);

        return CurrentBodyMetricsResponse.builder()
                .heightCm(currentHeight)
                .weightKg(latestOpt.map(m -> toDouble(m.getWeightKg())).orElse(null))
                .bodyFatPercentage(latestOpt.map(m -> toDouble(m.getBodyFatPercentage())).orElse(null))
                .waistCm(latestOpt.map(m -> toDouble(m.getWaistCm())).orElse(null))
                .chestCm(latestOpt.map(m -> toDouble(m.getChestCm())).orElse(null))
                .armCm(latestOpt.map(m -> toDouble(m.getArmCm())).orElse(null))
                .thighCm(latestOpt.map(m -> toDouble(m.getThighCm())).orElse(null))
                .measuredAt(latestOpt.map(ClientBodyMeasurement::getMeasuredAt).orElse(null))
                .totalMeasurementsCount(count)
                .build();
    }

    @Override
    @Transactional
    public BodyMeasurementResponse recordMeasurement(Long clientId, RecordBodyMeasurementRequest request) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (request.getMeasuredAt() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Measurement date is required");
        }

        // Clock skew allowance: 5 minutes
        if (request.getMeasuredAt().isAfter(ZonedDateTime.now().plusMinutes(5))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Measurement date cannot be in the future");
        }

        ClientBodyMeasurement measurement = ClientBodyMeasurement.builder()
                .client(client)
                .weightKg(toBigDecimal(request.getWeightKg()))
                .bodyFatPercentage(toBigDecimal(request.getBodyFatPercentage()))
                .waistCm(toBigDecimal(request.getWaistCm()))
                .chestCm(toBigDecimal(request.getChestCm()))
                .armCm(toBigDecimal(request.getArmCm()))
                .thighCm(toBigDecimal(request.getThighCm()))
                .measuredAt(request.getMeasuredAt())
                .build();

        ClientBodyMeasurement saved = measurementRepository.save(measurement);
        log.info("Recorded body measurement id={} for clientId={}", saved.getId(), clientId);

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public CurrentBodyMetricsResponse updateHeight(Long clientId, UpdateHeightRequest request) {
        User user = userRepository.findById(clientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        ClientProfile profile = clientProfileRepository.findByUserId(clientId)
                .orElseGet(() -> ClientProfile.builder()
                        .user(user)
                        .experienceLevel(user.getExperienceLevel())
                        .build());

        profile.setHeightCm(toBigDecimal(request.getHeightCm()));
        clientProfileRepository.save(profile);
        log.info("Updated height for clientId={}", clientId);

        return getCurrentMetrics(clientId);
    }

    @Override
    @Transactional(readOnly = true)
    public BodyMetricsHistoryResponse getMeasurementHistory(
            Long clientId,
            ZonedDateTime startDate,
            ZonedDateTime endDate,
            int page,
            int size,
            String sort
    ) {
        verifyUserExists(clientId);

        Sort.Direction direction = "asc".equalsIgnoreCase(sort) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, "measuredAt"));

        Page<ClientBodyMeasurement> measurementPage;
        if (startDate != null || endDate != null) {
            measurementPage = measurementRepository.findByClientIdWithDateRange(clientId, startDate, endDate, pageable);
        } else {
            measurementPage = measurementRepository.findByClientId(clientId, pageable);
        }

        Double currentHeight = clientProfileRepository.findByUserId(clientId)
                .map(ClientProfile::getHeightCm)
                .map(BodyMetricsServiceImpl::toDouble)
                .orElse(null);

        List<BodyMeasurementResponse> content = measurementPage.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return BodyMetricsHistoryResponse.builder()
                .content(content)
                .page(measurementPage.getNumber())
                .size(measurementPage.getSize())
                .totalElements(measurementPage.getTotalElements())
                .totalPages(measurementPage.getTotalPages())
                .currentHeightCm(currentHeight)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CoachClientBodyMetricsResponse getCoachClientBodyMetrics(
            Long coachUserId,
            Long clientUserId,
            ZonedDateTime startDate,
            ZonedDateTime endDate,
            int page,
            int size,
            String sort
    ) {
        boolean isAssigned = relationshipRepository.existsByCoachIdAndClientIdAndStatusIn(
                coachUserId,
                clientUserId,
                List.of(CoachClientRelationshipStatus.ACTIVE)
        );

        if (!isAssigned) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: You are not assigned to coach this client");
        }

        User client = userRepository.findById(clientUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client user not found"));

        CurrentBodyMetricsResponse currentMetrics = getCurrentMetrics(clientUserId);
        BodyMetricsHistoryResponse history = getMeasurementHistory(clientUserId, startDate, endDate, page, size, sort);

        return CoachClientBodyMetricsResponse.builder()
                .clientId(clientUserId)
                .clientName(client.getFullName())
                .currentHeightCm(currentMetrics.getHeightCm())
                .currentMetrics(currentMetrics)
                .measurements(history.getContent())
                .page(history.getPage())
                .size(history.getSize())
                .totalElements(history.getTotalElements())
                .totalPages(history.getTotalPages())
                .build();
    }

    private void verifyUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private BodyMeasurementResponse mapToResponse(ClientBodyMeasurement entity) {
        return BodyMeasurementResponse.builder()
                .id(entity.getId())
                .clientId(entity.getClient().getId())
                .weightKg(toDouble(entity.getWeightKg()))
                .bodyFatPercentage(toDouble(entity.getBodyFatPercentage()))
                .waistCm(toDouble(entity.getWaistCm()))
                .chestCm(toDouble(entity.getChestCm()))
                .armCm(toDouble(entity.getArmCm()))
                .thighCm(toDouble(entity.getThighCm()))
                .measuredAt(entity.getMeasuredAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }
}
