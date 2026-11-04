package com.calisthenics.backend.bodymetrics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachClientBodyMetricsResponse {

    private Long clientId;
    private String clientName;
    private Double currentHeightCm;
    private CurrentBodyMetricsResponse currentMetrics;
    private List<BodyMeasurementResponse> measurements;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}

