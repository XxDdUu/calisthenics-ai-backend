package com.calisthenics.backend.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPresenceResponse {
    private Long userId;
    private String status;
    private ZonedDateTime lastSeen;
    private long version;
}
