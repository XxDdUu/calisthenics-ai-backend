package com.calisthenics.backend.profile.dto.request;

import com.calisthenics.backend.profile.entity.ProfileType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReorderProfileMediaRequest {

    @NotNull(message = "Profile type is required")
    private ProfileType profileType;

    @NotEmpty(message = "Media IDs list cannot be empty")
    private List<Long> mediaIds;
}
