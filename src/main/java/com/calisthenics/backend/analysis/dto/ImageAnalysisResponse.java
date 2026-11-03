package com.calisthenics.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ImageAnalysisResponse extends AnalysisResponse {

    @Override
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public Long getExerciseId() {
        return super.getExerciseId();
    }

    public static ImageAnalysisResponse from(AnalysisResponse response) {
        ImageAnalysisResponse imageResponse = new ImageAnalysisResponse();
        imageResponse.setId(response.getId());
        imageResponse.setUserId(response.getUserId());
        imageResponse.setExerciseId(response.getExerciseId());
        imageResponse.setExerciseName(response.getExerciseName());
        imageResponse.setMediaUrl(response.getMediaUrl());
        imageResponse.setMediaType(response.getMediaType());
        imageResponse.setOverallScore(response.getOverallScore());
        imageResponse.setHoldDurationSeconds(response.getHoldDurationSeconds());
        imageResponse.setFormFeedback(response.getFormFeedback());
        imageResponse.setElbowAngle(response.getElbowAngle());
        imageResponse.setBodyLineAngle(response.getBodyLineAngle());
        imageResponse.setShoulderLeanAngle(response.getShoulderLeanAngle());
        imageResponse.setRawKeypointsJson(response.getRawKeypointsJson());
        imageResponse.setCreatedAt(response.getCreatedAt());
        return imageResponse;
    }
}
