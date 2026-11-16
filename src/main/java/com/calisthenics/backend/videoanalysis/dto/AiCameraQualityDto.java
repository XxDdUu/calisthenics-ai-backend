package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiCameraQualityDto {

    @JsonAlias({"overall_quality", "quality", "status"})
    private String overallQuality;

    @JsonAlias("quality_score")
    private Double score;

    @JsonAlias({"view_angle", "recommendedAngle", "recommended_angle", "detectedView", "detected_view"})
    private String viewAngle;

    @JsonAlias({"full_body_visible_ratio", "poseCoverageRatio", "pose_coverage_ratio", "coverage_ratio", "coverageRatio", "visibilityRatio"})
    private Double fullBodyVisibleRatio;

    @JsonAlias({"average_confidence", "mean_confidence", "meanConfidence"})
    private Double averageConfidence;

    @JsonAlias("missing_landmarks")
    private List<String> missingLandmarks;

    @JsonAlias("detected_frames")
    private Integer detectedFrames;

    @JsonAlias("sampled_frames")
    private Integer sampledFrames;

    @JsonAlias({"side_view_detected", "sideView", "side_view"})
    private Boolean sideViewDetected;

    @JsonAlias("issues")
    private List<String> warnings;

    @JsonAlias("summary")
    private String message;
}
