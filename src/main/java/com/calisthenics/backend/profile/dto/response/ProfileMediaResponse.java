package com.calisthenics.backend.profile.dto.response;

import com.calisthenics.backend.profile.entity.ProfileMediaType;
import com.calisthenics.backend.profile.entity.ProfileType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProfileMediaResponse {
    private Long id;
    private Long userId;
    private ProfileType profileType;
    private ProfileMediaType mediaType;
    private String url;
    private String thumbnailUrl;
    private int displayOrder;
    private ZonedDateTime createdAt;
}
