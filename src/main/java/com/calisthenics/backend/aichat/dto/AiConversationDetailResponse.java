package com.calisthenics.backend.aichat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationDetailResponse {

    private Long id;
    private String title;
    @Builder.Default
    private List<AiMessageResponse> messages = new ArrayList<>();
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
