package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiVideoKeypointsDataDto {
    private String format;
    private String coordinateSystem;
    private List<AiVideoKeypointFrameDto> frames;
}
