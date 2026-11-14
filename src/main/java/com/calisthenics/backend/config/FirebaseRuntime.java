package com.calisthenics.backend.config;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;

public record FirebaseRuntime(
        FirebaseApp app,
        FirebaseAuth auth,
        String projectId,
        boolean adminCredentialsLoaded
) {
    public boolean isAvailable() {
        return app != null && auth != null;
    }
}
