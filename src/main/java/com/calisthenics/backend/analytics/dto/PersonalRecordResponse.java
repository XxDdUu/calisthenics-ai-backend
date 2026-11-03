package com.calisthenics.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalRecordResponse {
    private Long id;
    private Long exerciseId;
    private String exerciseName;
    private Long sessionId;
    private String recordType;
    private BigDecimal recordValue;
    private String unit;
    private String notes;
    private BigDecimal secondaryValue;
    private BigDecimal previousRecordValue;
    private BigDecimal previousSecondaryValue;
    private BigDecimal improvementPercentage;
    private ZonedDateTime achievedAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private String formattedRecord;
    private String formattedPrevious;

    @JsonProperty("prType")
    public String getPrType() {
        return recordType;
    }

    @JsonProperty("value")
    public BigDecimal getValue() {
        return recordValue;
    }

    @JsonProperty("formattedValue")
    public String getFormattedValue() {
        return formattedRecord;
    }

    @JsonProperty("workoutSessionId")
    public Long getWorkoutSessionId() {
        return sessionId;
    }
}
