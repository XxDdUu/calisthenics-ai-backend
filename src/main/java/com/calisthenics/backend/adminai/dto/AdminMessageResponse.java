package com.calisthenics.backend.adminai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMessageResponse {
    private Long id;
    private Long conversationId;
    private String role;
    private String content;
    private String intent;
    private String metadata;
    private ZonedDateTime createdAt;
}
