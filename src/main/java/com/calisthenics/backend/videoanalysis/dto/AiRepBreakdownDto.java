package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiRepBreakdownDto {

    @JsonAlias("rep_number")
    private Integer repNumber;

    @JsonAlias("start_frame")
    private Integer startFrame;

    @JsonAlias("end_frame")
    private Integer endFrame;

    @JsonProperty("isValid")
    @JsonAlias({"valid", "is_valid"})
    private Boolean valid;

    @JsonAlias("completion_status")
    private String status;

    @JsonAlias({"start_timestamp_ms", "startMs", "start_ms"})
    private Long startTimestampMs;

    @JsonAlias({"end_timestamp_ms", "endMs", "end_ms"})
    private Long endTimestampMs;

    @JsonAlias("duration_ms")
    private Long durationMs;

    @JsonAlias({"form_score", "score"})
    private Double formScore;

    @JsonAlias({"min_angle_deg", "minJointAngle", "min_joint_angle", "minimum_joint_angle", "minimumJointAngle", "min_elbow_angle", "min_knee_angle"})
    private Double minAngleDeg;

    @JsonAlias({"max_angle_deg", "maxJointAngle", "max_joint_angle", "maximum_joint_angle", "maximumJointAngle"})
    private Double maxAngleDeg;

    @JsonAlias("rom_deg")
    private Double romDeg;

    @JsonAlias({"depth_classification", "depthRating", "depth_rating", "depth"})
    private String depthClassification;

    @JsonAlias({"key_metrics", "metrics"})
    private Map<String, Object> keyMetrics;

    @JsonAlias({"errors", "form_errors", "detected_errors", "detectedErrors"})
    private List<String> issues;

    @JsonDeserialize(using = FeedbackDeserializer.class)
    private String feedback;

    public static class FeedbackDeserializer extends JsonDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            JsonNode node = p.readValueAsTree();
            if (node == null || node.isNull()) {
                return null;
            }
            if (node.isTextual()) {
                return node.asText();
            }
            if (node.isArray()) {
                List<String> parts = new ArrayList<>();
                for (JsonNode item : node) {
                    if (item != null && !item.isNull()) {
                        String text = item.asText();
                        if (text != null && !text.isBlank()) {
                            parts.add(text.trim());
                        }
                    }
                }
                return parts.isEmpty() ? null : String.join("; ", parts);
            }
            return node.asText();
        }
    }
}
