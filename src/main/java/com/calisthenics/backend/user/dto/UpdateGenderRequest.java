package com.calisthenics.backend.user.dto;

import com.calisthenics.backend.user.Gender;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGenderRequest {

    @NotNull(message = "Gender is required")
    private Gender gender;
}
