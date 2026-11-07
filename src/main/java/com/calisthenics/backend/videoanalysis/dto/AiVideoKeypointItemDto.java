package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiVideoKeypointItemDto {
    private String name;
    private Float x;
    private Float y;
    private Float confidence;
}
