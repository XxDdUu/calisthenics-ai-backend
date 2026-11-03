package com.calisthenics.backend.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class UnreadCountResponse {
    private long unreadCount;
}
