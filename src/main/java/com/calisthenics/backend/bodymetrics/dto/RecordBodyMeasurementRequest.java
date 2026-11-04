package com.calisthenics.backend.bodymetrics.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecordBodyMeasurementRequest {

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "20.0", message = "Weight must be at least 20 kg")
    @DecimalMax(value = "500.0", message = "Weight must not exceed 500 kg")
    private Double weightKg;

    @DecimalMin(value = "0.0", message = "Body fat percentage cannot be negative")
    @DecimalMax(value = "100.0", message = "Body fat percentage cannot exceed 100%")
    private Double bodyFatPercentage;

    @DecimalMin(value = "20.0", message = "Waist circumference must be at least 20 cm")
    @DecimalMax(value = "300.0", message = "Waist circumference must not exceed 300 cm")
    private Double waistCm;

    @DecimalMin(value = "20.0", message = "Chest circumference must be at least 20 cm")
    @DecimalMax(value = "300.0", message = "Chest circumference must not exceed 300 cm")
    private Double chestCm;

    @DecimalMin(value = "10.0", message = "Arm circumference must be at least 10 cm")
    @DecimalMax(value = "150.0", message = "Arm circumference must not exceed 150 cm")
    private Double armCm;

    @DecimalMin(value = "15.0", message = "Thigh circumference must be at least 15 cm")
    @DecimalMax(value = "200.0", message = "Thigh circumference must not exceed 200 cm")
    private Double thighCm;

    @NotNull(message = "Measurement timestamp is required")
    private ZonedDateTime measuredAt;
}

