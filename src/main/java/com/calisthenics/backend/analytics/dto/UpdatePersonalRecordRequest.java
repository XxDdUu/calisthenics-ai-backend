package com.calisthenics.backend.analytics.dto;

import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
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
public class UpdatePersonalRecordRequest {

    @JsonAlias({"recordType"})
    private PersonalRecordType prType;

    @DecimalMin(value = "0.01", message = "PR value must be greater than zero")
    @JsonAlias({"recordValue"})
    private BigDecimal value;

    private String unit;

    private ZonedDateTime achievedAt;

    private String notes;
}

