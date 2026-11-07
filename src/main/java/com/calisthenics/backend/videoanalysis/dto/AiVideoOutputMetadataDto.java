package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiVideoOutputMetadataDto {
    private Boolean originalAvailable;
    private Boolean annotatedAvailable;
    private String originalObjectKey;
    private String annotatedObjectKey;
    private Long durationMs;
    private Integer width;
    private Integer height;
    private Double fps;
}
