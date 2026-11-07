package com.calisthenics.backend.videoanalysis.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoAnalysisKeypointsResponseDto {
    private Long analysisId;
    @Builder.Default
    private String format = "COCO_17";
    @Builder.Default
    private String coordinateSystem = "NORMALIZED";
    private int totalFrames;
    private List<AiVideoKeypointFrameDto> frames;
}
