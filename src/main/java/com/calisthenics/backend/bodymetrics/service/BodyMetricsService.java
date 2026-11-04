package com.calisthenics.backend.bodymetrics.service;

import com.calisthenics.backend.bodymetrics.dto.*;

import java.time.ZonedDateTime;

public interface BodyMetricsService {

    CurrentBodyMetricsResponse getCurrentMetrics(Long clientId);

    BodyMeasurementResponse recordMeasurement(Long clientId, RecordBodyMeasurementRequest request);

    CurrentBodyMetricsResponse updateHeight(Long clientId, UpdateHeightRequest request);

    BodyMetricsHistoryResponse getMeasurementHistory(
            Long clientId,
            ZonedDateTime startDate,
            ZonedDateTime endDate,
            int page,
            int size,
            String sort
    );

    CoachClientBodyMetricsResponse getCoachClientBodyMetrics(
            Long coachUserId,
            Long clientUserId,
            ZonedDateTime startDate,
            ZonedDateTime endDate,
            int page,
            int size,
            String sort
    );
}

