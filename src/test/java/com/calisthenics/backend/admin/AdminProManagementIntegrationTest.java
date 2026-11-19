package com.calisthenics.backend.admin;

import com.calisthenics.backend.analysis.AiServiceClient;
import com.calisthenics.backend.analysis.LiveAnalysisService;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.storage.MinioAnalysisMediaStorageService;
import com.calisthenics.backend.user.EntitlementGrantSource;
import com.calisthenics.backend.user.EntitlementStatus;
import com.calisthenics.backend.user.MembershipEntitlement;
import com.calisthenics.backend.user.MembershipEntitlementRepository;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.videoanalysis.VideoAnalysis;
import com.calisthenics.backend.videoanalysis.VideoAnalysisRepository;
import com.calisthenics.backend.videoanalysis.dto.AiVideoScoringResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminProManagementIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MembershipEntitlementRepository entitlementRepository;

    @Autowired
    private AdminProAuditLogRepository auditLogRepository;

    @Autowired
    private VideoAnalysisRepository videoAnalysisRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired(required = false)
    @Qualifier("taskExecutor")
    private ThreadPoolTaskExecutor appTaskExecutor;

    @MockBean
    private MinioAnalysisMediaStorageService minioStorageService;

    @MockBean
    private AiServiceClient aiServiceClient;

    @MockBean
    private LiveAnalysisService liveAnalysisService;

    private final List<Long> createdUserIds = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws InterruptedException {
        awaitAsyncExecutorIdle();
        createdUserIds.clear();
        reset(minioStorageService, aiServiceClient, liveAnalysisService);

        when(minioStorageService.store(any()))
                .thenAnswer(inv -> UUID.randomUUID() + "_orig.mp4");
        when(aiServiceClient.scoreVideo(any(byte[].class), any(), any(), anyInt(), any(), anyBoolean()))
                .thenReturn(AiVideoScoringResponseDto.builder()
                        .schemaVersion("1.0")
                        .analysisId(UUID.randomUUID().toString())
                        .status("COMPLETED")
                        .exercise("SQUAT")
                        .exerciseCode("SQUAT")
                        .score(85.0)
                        .repCount(1)
                        .validReps(1)
                        .incompleteReps(0)
                        .build());

        ObjectNode liveOk = objectMapper.createObjectNode();
        liveOk.put("status", "VALID");
        liveOk.put("exercise", "SQUAT");
        when(liveAnalysisService.analyzeFrameSync(
                any(), anyString(), anyString(), anyLong(), anyLong(), anyBoolean(), anyBoolean(), anyBoolean()))
                .thenReturn(liveOk);
    }

    private void awaitAsyncExecutorIdle() throws InterruptedException {
        if (appTaskExecutor == null) {
            return;
        }
        long deadline = System.currentTimeMillis() + 20000L;
        while (System.currentTimeMillis() < deadline) {
            int active = appTaskExecutor.getActiveCount();
            boolean queueEmpty = appTaskExecutor.getThreadPoolExecutor().getQueue().isEmpty();
            if (active == 0 && queueEmpty) {
                return;
            }
            Thread.sleep(25L);
        }
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        awaitAsyncExecutorIdle();
        List<Long> userIds = new ArrayList<>(createdUserIds);
        createdUserIds.clear();
        for (Long userId : userIds) {
            for (VideoAnalysis analysis : videoAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
                long deadline = System.currentTimeMillis() + 20000L;
                while (System.currentTimeMillis() < deadline) {
                    var latest = videoAnalysisRepository.findById(analysis.getId()).orElse(null);
                    if (latest == null || (!"PENDING".equalsIgnoreCase(latest.getStatus())
                            && !"PROCESSING".equalsIgnoreCase(latest.getStatus()))) {
                        break;
                    }
                    Thread.sleep(30L);
                }
            }
            awaitAsyncExecutorIdle();
            jdbcTemplate.update("DELETE FROM admin_pro_audit_logs WHERE target_user_id = ? OR admin_user_id = ?", userId, userId);
            jdbcTemplate.update("DELETE FROM membership_entitlements WHERE user_id = ?", userId);
            jdbcTemplate.update(
                    "DELETE FROM video_analysis_keypoints WHERE video_analysis_id IN (SELECT id FROM video_analyses WHERE user_id = ?)",
                    userId
            );
            jdbcTemplate.update("DELETE FROM video_analyses WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM video_analysis_quota_usages WHERE user_id = ?", userId);
            jdbcTemplate.update(
                    "DELETE FROM rep_histories WHERE session_id IN (SELECT id FROM analysis_sessions WHERE user_id = ?)",
                    userId
            );
            jdbcTemplate.update("DELETE FROM analysis_sessions WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
    }

    @Test
    void adminCanSearchUsersByEmailUsernameAndId() throws Exception {
        User admin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Platform Admin");
        User target = createUser(Role.ROLE_USER, MembershipTier.FREE, "Alex Calisthenics");
        String adminJwt = bearerToken(admin);

        // Search by user ID
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", String.valueOf(target.getId()))
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(target.getId()))
                .andExpect(jsonPath("$.content[0].membershipTier").value("FREE"))
                .andExpect(jsonPath("$.content[0].adminGrantedPro").value(false));

        // Search by username (fullName)
        mockMvc.perform(get("/api/v1/admin/pro/users")
                        .param("search", "Alex Calisthenics")
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(target.getId()));

        // Search by email
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("search", target.getEmail())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(target.getId()));
    }

    @Test
    void adminGrantPro_givesUnlimitedProFeaturesWithoutPaymentAndRecordsAuditLog() throws Exception {
        User admin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Admin One");
        User regularUser = createUser(Role.ROLE_USER, MembershipTier.FREE, "Athlete Free");
        String adminJwt = bearerToken(admin);
        String userJwt = bearerToken(regularUser);

        // Grant PRO via admin endpoint (no payment required)
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", regularUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"VIP beta tester\"}")
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(regularUser.getId()))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.membershipSource").value("Admin Grant"))
                .andExpect(jsonPath("$.unlimited").value(true))
                .andExpect(jsonPath("$.adminGrantedPro").value(true))
                .andExpect(jsonPath("$.adminProStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.adminProGrantSource").value("ADMIN_GRANT"))
                .andExpect(jsonPath("$.grantedByAdminId").value(admin.getId()))
                .andExpect(jsonPath("$.adminProGrantedAt").exists());

        // Role must NOT be elevated
        User refreshedUser = userRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(refreshedUser.getRole()).isEqualTo(Role.ROLE_USER);

        // Verify entitlement record in DB
        MembershipEntitlement entitlement = entitlementRepository
                .findByUserIdAndGrantSource(regularUser.getId(), EntitlementGrantSource.ADMIN_GRANT)
                .orElseThrow();
        assertThat(entitlement.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);
        assertThat(entitlement.isUnlimited()).isTrue();
        assertThat(entitlement.getGrantedByAdminId()).isEqualTo(admin.getId());
        assertThat(entitlement.getReason()).isEqualTo("VIP beta tester");

        // Verify audit log recorded
        var auditLogs = auditLogRepository.findByTargetUserIdOrderByCreatedAtDesc(
                regularUser.getId(),
                org.springframework.data.domain.PageRequest.of(0, 10)
        );
        assertThat(auditLogs.getContent()).hasSize(1);
        var grantLog = auditLogs.getContent().get(0);
        assertThat(grantLog.getAction()).isEqualTo(AdminProAuditAction.GRANT_PRO);
        assertThat(grantLog.getAdminUserId()).isEqualTo(admin.getId());
        assertThat(grantLog.getPreviousEffectiveTier()).isEqualTo("FREE");
        assertThat(grantLog.getNewEffectiveTier()).isEqualTo("PRO");
        assertThat(grantLog.getNewStatus()).isEqualTo("ACTIVE");

        // Verify UNLIMITED video uploads (> 10 paid PRO limit, e.g. 12 uploads!) with existing JWT
        for (int i = 1; i <= 12; i++) {
            mockMvc.perform(multipart("/api/v1/video-analysis")
                            .file(sampleVideoFile())
                            .param("exerciseType", "SQUAT")
                            .header("Authorization", userJwt))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.quota.membershipTier").value("PRO"))
                    .andExpect(jsonPath("$.quota.unlimited").value(true))
                    .andExpect(jsonPath("$.quota.dailyLimit").doesNotExist())
                    .andExpect(jsonPath("$.quota.usedToday").value(i));
        }

        // Verify Live Analysis is unlocked
        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(sampleFrameFile())
                        .param("exercise", "SQUAT")
                        .param("session_id", "admin-pro-sess-1")
                        .header("Authorization", userJwt))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/analysis/sessions")
                        .header("Authorization", userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sessionId\":\"admin-pro-sess-1\",\"exerciseType\":\"SQUAT\",\"cameraView\":\"SIDE\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void adminRevokePro_revertsFreeUserAndRecordsAuditLog() throws Exception {
        User admin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Admin Revoker");
        User user = createUser(Role.ROLE_USER, MembershipTier.FREE, "Temporary Pro");
        String adminJwt = bearerToken(admin);
        String userJwt = bearerToken(user);

        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", user.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/revoke", user.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Trial ended\"}")
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.unlimited").value(false))
                .andExpect(jsonPath("$.adminGrantedPro").value(false))
                .andExpect(jsonPath("$.adminProStatus").value("REVOKED"))
                .andExpect(jsonPath("$.revokedByAdminId").value(admin.getId()))
                .andExpect(jsonPath("$.adminProRevokedAt").exists());

        // Existing JWT immediately loses PRO access
        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(sampleFrameFile())
                        .param("exercise", "SQUAT")
                        .param("session_id", "revoked-sess")
                        .header("Authorization", userJwt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRO_MEMBERSHIP_REQUIRED"));

        // Audit logs contain both GRANT_PRO and REVOKE_PRO
        mockMvc.perform(get("/api/v1/admin/users/{userId}/pro/audit-logs", user.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].action").value("REVOKE_PRO"))
                .andExpect(jsonPath("$.content[1].action").value("GRANT_PRO"));
    }

    @Test
    void revokingAdminGrantedPro_preservesIndependentPaidSubscription() throws Exception {
        User admin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Admin One");
        User paidProUser = createUser(Role.ROLE_USER, MembershipTier.PRO, "Paying Member");
        String adminJwt = bearerToken(admin);
        String userJwt = bearerToken(paidProUser);

        // Before admin grant: paid PRO (10/day, not unlimited)
        mockMvc.perform(get("/api/v1/users/me/usage").header("Authorization", userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.tier").value("PRO"))
                .andExpect(jsonPath("$.membership.source").value("Subscription"))
                .andExpect(jsonPath("$.videoAnalysis.limit").value(10))
                .andExpect(jsonPath("$.videoAnalysis.unlimited").value(false));

        // Admin grants unlimited PRO on top of paid subscription
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", paidProUser.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidPro").value(true))
                .andExpect(jsonPath("$.adminGrantedPro").value(true))
                .andExpect(jsonPath("$.unlimited").value(true))
                .andExpect(jsonPath("$.membershipSource").value("Admin Grant"));

        mockMvc.perform(get("/api/v1/users/me/usage").header("Authorization", userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.tier").value("PRO"))
                .andExpect(jsonPath("$.membership.source").value("Admin Grant"))
                .andExpect(jsonPath("$.videoAnalysis.unlimited").value(true))
                .andExpect(jsonPath("$.liveAnalysis.unlimited").value(true));

        // Admin revokes admin-granted PRO
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/revoke", paidProUser.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.membershipSource").value("Subscription"))
                .andExpect(jsonPath("$.paidPro").value(true))
                .andExpect(jsonPath("$.adminGrantedPro").value(false))
                .andExpect(jsonPath("$.unlimited").value(false));

        // Paid subscription remains completely intact (10/day + live analysis unlocked)
        mockMvc.perform(get("/api/v1/users/me/usage").header("Authorization", userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.tier").value("PRO"))
                .andExpect(jsonPath("$.membership.source").value("Subscription"))
                .andExpect(jsonPath("$.videoAnalysis.limit").value(10))
                .andExpect(jsonPath("$.videoAnalysis.unlimited").value(false))
                .andExpect(jsonPath("$.liveAnalysis.available").value(true));

        // Attempting to revoke again when only paid subscription remains returns 409 and does NOT touch paid PRO
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/revoke", paidProUser.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isConflict());

        User persisted = userRepository.findById(paidProUser.getId()).orElseThrow();
        assertThat(persisted.getMembershipTier()).isEqualTo(MembershipTier.PRO);
    }

    @Test
    void nonAdminRequestsReceive403AndCannotManipulateProEntitlements() throws Exception {
        User regularUser = createUser(Role.ROLE_USER, MembershipTier.FREE, "Regular User");
        User otherUser = createUser(Role.ROLE_USER, MembershipTier.FREE, "Target User");
        User admin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Admin User");
        String userJwt = bearerToken(regularUser);
        String adminJwt = bearerToken(admin);

        // Non-admin blocked from all admin PRO endpoints with HTTP 403
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/pro/users").header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", regularUser.getId()).header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", otherUser.getId()).header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/revoke", otherUser.getId()).header("Authorization", userJwt))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/pro/audit-logs").header("Authorization", userJwt))
                .andExpect(status().isForbidden());

        // Profile update payload manipulation cannot escalate role or grant PRO
        mockMvc.perform(put("/api/v1/users/me")
                        .header("Authorization", userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Regular User\",\"role\":\"ROLE_ADMIN\",\"membershipTier\":\"PRO\",\"unlimited\":true,\"adminGrantedPro\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.unlimited").value(false))
                .andExpect(jsonPath("$.adminGrantedPro").value(false));

        // Regular user cannot self-grant ADMIN via membership endpoint
        mockMvc.perform(put("/api/v1/users/me/membership")
                        .header("Authorization", userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipTier\":\"ADMIN\"}"))
                .andExpect(status().isForbidden());

        // When user has active admin-granted PRO, user cannot tamper with it via /me/membership
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", regularUser.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/users/me/membership")
                        .header("Authorization", userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipTier\":\"FREE\"}"))
                .andExpect(status().isForbidden());

        // Admin-granted PRO remains active and unlimited
        mockMvc.perform(get("/api/v1/users/me/entitlements").header("Authorization", userJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.unlimited").value(true))
                .andExpect(jsonPath("$.adminGrantedPro").value(true));
    }

    @Test
    void existingJwtSessionReflectsProChangesImmediatelyWithoutRelogin() throws Exception {
        User admin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Admin One");
        User user = createUser(Role.ROLE_USER, MembershipTier.FREE, "Session User");
        String adminJwt = bearerToken(admin);
        // Mint user's JWT BEFORE any PRO grant exists
        String existingUserJwt = bearerToken(user);

        // 1. Before grant: existing JWT sees FREE and is blocked from live analysis
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", existingUserJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.unlimited").value(false))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(false));

        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(sampleFrameFile())
                        .param("exercise", "SQUAT")
                        .param("session_id", "jwt-sess-1")
                        .header("Authorization", existingUserJwt))
                .andExpect(status().isForbidden());

        // 2. Admin grants PRO
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", user.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk());

        // 3. Same existing JWT immediately reflects PRO unlimited in profile, usage, and authorization checks
        mockMvc.perform(get("/api/v1/users/me").header("Authorization", existingUserJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.unlimited").value(true))
                .andExpect(jsonPath("$.adminGrantedPro").value(true))
                .andExpect(jsonPath("$.membershipSource").value("Admin Grant"))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(true));

        mockMvc.perform(get("/api/v1/users/me/usage").header("Authorization", existingUserJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membership.tier").value("PRO"))
                .andExpect(jsonPath("$.membership.source").value("Admin Grant"))
                .andExpect(jsonPath("$.videoAnalysis.unlimited").value(true))
                .andExpect(jsonPath("$.liveAnalysis.available").value(true))
                .andExpect(jsonPath("$.liveAnalysis.unlimited").value(true));

        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(sampleFrameFile())
                        .param("exercise", "SQUAT")
                        .param("session_id", "jwt-sess-1")
                        .header("Authorization", existingUserJwt))
                .andExpect(status().isOk());

        // 4. Admin revokes PRO -> same existing JWT immediately sees FREE and 403 again
        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/revoke", user.getId())
                        .header("Authorization", adminJwt))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/me").header("Authorization", existingUserJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("FREE"))
                .andExpect(jsonPath("$.unlimited").value(false))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(false));

        mockMvc.perform(multipart("/api/v1/analysis/live/frame")
                        .file(sampleFrameFile())
                        .param("exercise", "SQUAT")
                        .param("session_id", "jwt-sess-1")
                        .header("Authorization", existingUserJwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void grantingAndRevokingProOnAdminAccountDoesNotAlterAdminRole() throws Exception {
        User superAdmin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Super Admin");
        User targetAdmin = createUser(Role.ROLE_ADMIN, MembershipTier.FREE, "Target Admin");
        String superAdminJwt = bearerToken(superAdmin);

        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/grant", targetAdmin.getId())
                        .header("Authorization", superAdminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.membershipTier").value("ADMIN"))
                .andExpect(jsonPath("$.adminGrantedPro").value(true));

        mockMvc.perform(post("/api/v1/admin/users/{userId}/pro/revoke", targetAdmin.getId())
                        .header("Authorization", superAdminJwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_ADMIN"))
                .andExpect(jsonPath("$.membershipTier").value("ADMIN"))
                .andExpect(jsonPath("$.unlimited").value(true));

        User persistedAdmin = userRepository.findById(targetAdmin.getId()).orElseThrow();
        assertThat(persistedAdmin.getRole()).isEqualTo(Role.ROLE_ADMIN);
    }

    private User createUser(Role role, MembershipTier tier, String fullName) {
        User saved = userRepository.saveAndFlush(User.builder()
                .email("pro_test_" + UUID.randomUUID() + "@example.com")
                .fullName(fullName)
                .role(role)
                .membershipTier(tier)
                .build());
        createdUserIds.add(saved.getId());
        return saved;
    }

    private String bearerToken(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(user));
    }

    private MockMultipartFile sampleVideoFile() {
        return new MockMultipartFile("video", "workout.mp4", "video/mp4", "synthetic-mp4".getBytes());
    }

    private MockMultipartFile sampleFrameFile() {
        return new MockMultipartFile("file", "frame.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2, 3, 4});
    }
}
