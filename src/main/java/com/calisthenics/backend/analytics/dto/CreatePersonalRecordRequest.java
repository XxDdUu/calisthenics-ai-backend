package com.calisthenics.backend.analytics.dto;

import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
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
public class CreatePersonalRecordRequest {

    private Long exerciseId;

    private String exerciseName;

    @NotNull(message = "PR type is required")
    @JsonAlias({"recordType"})
    private PersonalRecordType prType;

    @NotNull(message = "PR value is required")
    @DecimalMin(value = "0.01", message = "PR value must be greater than zero")
    @JsonAlias({"recordValue"})
    private BigDecimal value;

    private String unit;

    @NotNull(message = "Achievement date is required")
    private ZonedDateTime achievedAt;

    private String notes;
}

