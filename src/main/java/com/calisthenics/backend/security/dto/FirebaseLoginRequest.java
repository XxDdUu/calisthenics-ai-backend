package com.calisthenics.backend.security.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FirebaseLoginRequest {

    @NotBlank(message = "Firebase ID Token is required")
    private String idToken;
    
    private String experienceLevel = "INTERMEDIATE";
}
