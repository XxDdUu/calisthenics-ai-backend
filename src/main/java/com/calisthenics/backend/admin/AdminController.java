package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.*;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/dashboard")
    public ResponseEntity<AdminDashboardResponse> getDashboard() {
        return ResponseEntity.ok(adminService.getDashboard());
    }

    @GetMapping("/users")
    public ResponseEntity<AdminPageResponse<AdminUserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role role,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDirection
    ) {
        return ResponseEntity.ok(adminService.getUsers(page, size, search, role, sortBy, sortDirection));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminUserResponse> getUser(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getUser(userId));
    }

    @GetMapping("/users/{userId}/role")
    public ResponseEntity<AdminUserRoleResponse> getUserRole(@PathVariable Long userId) {
        return ResponseEntity.ok(adminService.getUserRole(userId));
    }

    @PutMapping("/users/{userId}/role")
    public ResponseEntity<AdminUserResponse> updateUserRole(
            @PathVariable Long userId,
            @Valid @RequestBody UpdateAdminUserRoleRequest request,
            @AuthenticationPrincipal UserPrincipal currentAdmin
    ) {
        return ResponseEntity.ok(adminService.updateUserRole(userId, request.role(), currentAdmin.getId()));
    }

    @GetMapping("/workout-plans")
    public ResponseEntity<AdminPageResponse<AdminWorkoutPlanSummaryResponse>> getWorkoutPlans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getWorkoutPlans(page, size));
    }

    @GetMapping("/workout-plans/{planId}")
    public ResponseEntity<AdminWorkoutPlanDetailResponse> getWorkoutPlan(@PathVariable Long planId) {
        return ResponseEntity.ok(adminService.getWorkoutPlan(planId));
    }

    @GetMapping("/workout-sessions")
    public ResponseEntity<AdminPageResponse<AdminWorkoutSessionSummaryResponse>> getWorkoutSessions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(adminService.getWorkoutSessions(page, size));
    }

    @GetMapping("/workout-sessions/{sessionId}")
    public ResponseEntity<AdminWorkoutSessionDetailResponse> getWorkoutSession(
            @PathVariable Long sessionId
    ) {
        return ResponseEntity.ok(adminService.getWorkoutSession(sessionId));
    }
}
