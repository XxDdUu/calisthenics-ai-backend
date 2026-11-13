package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTimeRangeMetadata {
    private String range;
    private LocalDate startDate;
    private LocalDate endDate;
    private long periodDays;
}
