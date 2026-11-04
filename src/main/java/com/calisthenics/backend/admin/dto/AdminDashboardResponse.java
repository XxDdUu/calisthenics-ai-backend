package com.calisthenics.backend.admin.dto;

public record AdminDashboardResponse(
        long totalUsers,
        long totalClients,
        long totalCoaches,
        long totalAdmins,
        long totalWorkoutSessions,
        long totalWorkoutPlans,
        long totalExercises
) {
    public AdminDashboardResponse(
            long totalUsers,
            long totalClients,
            long totalCoaches,
            long totalAdmins,
            long totalWorkoutSessions,
            long totalWorkoutPlans
    ) {
        this(totalUsers, totalClients, totalCoaches, totalAdmins, totalWorkoutSessions, totalWorkoutPlans, 0L);
    }
}
