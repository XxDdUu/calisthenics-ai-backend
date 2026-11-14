package com.calisthenics.backend.security.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FirebaseUserInfo {
    private String uid;
    private String email;
    private boolean emailVerified;
    private String name;
    private String picture;
    private String googleId;
}
