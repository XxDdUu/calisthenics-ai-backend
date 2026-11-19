package com.calisthenics.backend.aiworkout.gemini.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiResponseDto {

    private List<Candidate> candidates;
    private UsageMetadata usageMetadata;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Candidate {
        private Content content;
        private String finishReason;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Content {
        private List<Part> parts;
        private String role;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Part {
        private String text;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UsageMetadata {
        private Integer promptTokenCount;
        private Integer candidatesTokenCount;
        private Integer totalTokenCount;
    }

    public String getFirstText() {
        if (candidates != null && !candidates.isEmpty()
                && candidates.get(0).getContent() != null
                && candidates.get(0).getContent().getParts() != null
                && !candidates.get(0).getContent().getParts().isEmpty()) {
            return candidates.get(0).getContent().getParts().get(0).getText();
        }
        return null;
    }

    public String getFirstFinishReason() {
        if (candidates != null && !candidates.isEmpty()) {
            return candidates.get(0).getFinishReason();
        }
        return null;
    }
}
