package com.calisthenics.backend.bodymetrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BodyMeasurementResponse {

    private Long id;
    private Long clientId;
    private Double weightKg;
    private Double bodyFatPercentage;
    private Double waistCm;
    private Double chestCm;
    private Double armCm;
    private Double thighCm;
    private ZonedDateTime measuredAt;
    private ZonedDateTime createdAt;
}

