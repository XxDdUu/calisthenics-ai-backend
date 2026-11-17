package com.calisthenics.backend.adminai;

import com.calisthenics.backend.admin.AdminProAuditAction;
import com.calisthenics.backend.admin.AdminProAuditLog;
import com.calisthenics.backend.admin.AdminProAuditLogRepository;
import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.data.AdminConfirmationDecisionRequest;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecuteRequest;
import com.calisthenics.backend.adminai.entity.AdminAiAuditLog;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.repository.AdminAiPendingActionRepository;
import com.calisthenics.backend.adminai.service.AdminProToolService;
import com.calisthenics.backend.adminai.service.AdminToolRouter;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.EntitlementGrantSource;
import com.calisthenics.backend.user.EntitlementStatus;
import com.calisthenics.backend.user.MembershipEntitlement;
import com.calisthenics.backend.user.MembershipEntitlementRepository;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiProManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ClientProfileRepository clientProfileRepository;
    @Autowired private MembershipEntitlementRepository entitlementRepository;
    @Autowired private AdminProAuditLogRepository proAuditLogRepository;
    @Autowired private AdminAiAuditLogRepository aiAuditLogRepository;
    @Autowired private AdminAiPendingActionRepository pendingActionRepository;
    @Autowired private AdminToolRouter adminToolRouter;
    @Autowired private AdminProToolService adminProToolService;

    @MockBean
    private GeminiApiClient geminiApiClient;

    private User adminUser;
    private User freeTargetByEmail;
    private User handleTargetUser;
    private User paidProUser;
    private User ambiguousAlexOne;
    private User ambiguousAlexTwo;

    private String adminToken;
    private String nonAdminToken;
    private String targetEmailToken;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.callAdminAgent(anyString(), anyList())).thenReturn(Optional.empty());
        cleanDb();
        seedUsers();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanDb();
    }

    @Test
    void grantProByEmailRequiresExplicitConfirmationThenExecutesWithUnlimitedAccessAndAuditLogs() throws Exception {
        MvcResult step1 = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Grant PRO to user@example.com")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.pendingConfirmation.actionType").value("GRANT_PRO"))
                .andExpect(jsonPath("$.pendingConfirmation.targetUserId").value(freeTargetByEmail.getId()))
                .andExpect(jsonPath("$.pendingConfirmation.targetUserEmail").value("user@example.com"))
                .andExpect(jsonPath("$.updatedProUser").doesNotExist())
                .andReturn();

        // DB entitlement MUST NOT exist before explicit confirmation
        assertThat(entitlementRepository.findByUserIdAndGrantSource(
                freeTargetByEmail.getId(), EntitlementGrantSource.ADMIN_GRANT)).isEmpty();

        JsonNode body = objectMapper.readTree(step1.getResponse().getContentAsString());
        long conversationId = body.get("conversationId").asLong();
        String confirmationToken = body.get("pendingConfirmation").get("confirmationToken").asText();

        mockMvc.perform(post("/api/v1/admin/ai/confirmations/{token}", confirmationToken)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminConfirmationDecisionRequest.builder()
                                .approved(true)
                                .conversationId(conversationId)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.actionType").value("GRANT_PRO"))
                .andExpect(jsonPath("$.updatedUser.id").value(freeTargetByEmail.getId()))
                .andExpect(jsonPath("$.updatedUser.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.updatedUser.adminGrantedPro").value(true))
                .andExpect(jsonPath("$.updatedUser.unlimited").value(true));

        // Verify target user's quota reflects unlimited PRO access immediately
        mockMvc.perform(get("/api/v1/video-analysis/quota")
                        .header("Authorization", "Bearer " + targetEmailToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.adminGrantedPro").value(true))
                .andExpect(jsonPath("$.liveAnalysisUnlocked").value(true))
                .andExpect(jsonPath("$.unlimited").value(true));

        // Verify both admin_pro_audit_logs and admin_ai_audit_logs recorded the operation
        List<AdminProAuditLog> proLogs = proAuditLogRepository.findAll();
        assertThat(proLogs).hasSize(1);
        assertThat(proLogs.get(0).getAction()).isEqualTo(AdminProAuditAction.GRANT_PRO);
        assertThat(proLogs.get(0).getAdminUserId()).isEqualTo(adminUser.getId());
        assertThat(proLogs.get(0).getTargetUserId()).isEqualTo(freeTargetByEmail.getId());

        List<AdminAiAuditLog> aiLogs = aiAuditLogRepository.findTop50ByOrderByCreatedAtDesc();
        assertThat(aiLogs.stream()
                .anyMatch(l -> "TOOL_GRANT_PRO".equals(l.getAction())
                        && freeTargetByEmail.getId().equals(l.getTargetId())
                        && l.getDetails() != null
                        && l.getDetails().contains("EXECUTED"))).isTrue();
    }

    @Test
    void grantAndRevokeByUsernameSupportsNaturalLanguageConfirmation() throws Exception {
        // Step 1: Make user123 a PRO member -> Confirmation Required
        MvcResult grantStep1 = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Make user123 a PRO member")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.pendingConfirmation.actionType").value("GRANT_PRO"))
                .andExpect(jsonPath("$.pendingConfirmation.targetUserId").value(handleTargetUser.getId()))
                .andReturn();

        long convId = objectMapper.readTree(grantStep1.getResponse().getContentAsString())
                .get("conversationId").asLong();

        assertThat(entitlementRepository.findByUserIdAndGrantSource(
                handleTargetUser.getId(), EntitlementGrantSource.ADMIN_GRANT)).isEmpty();

        // Step 2: Confirm via natural-language message in the same conversation
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(convId)
                                .message("Confirm")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PRO_MANAGEMENT"))
                .andExpect(jsonPath("$.pendingConfirmation").doesNotExist())
                .andExpect(jsonPath("$.updatedProUser.id").value(handleTargetUser.getId()))
                .andExpect(jsonPath("$.updatedProUser.adminGrantedPro").value(true))
                .andExpect(jsonPath("$.updatedProUser.unlimited").value(true));

        // Step 3: Remove PRO access from user123 -> Confirmation Required
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(convId)
                                .message("Remove PRO access from user123")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.pendingConfirmation.actionType").value("REVOKE_PRO"))
                .andExpect(jsonPath("$.pendingConfirmation.targetUserId").value(handleTargetUser.getId()));

        // Still active until confirmed
        MembershipEntitlement stillActive = entitlementRepository
                .findByUserIdAndGrantSource(handleTargetUser.getId(), EntitlementGrantSource.ADMIN_GRANT)
                .orElseThrow();
        assertThat(stillActive.getStatus()).isEqualTo(EntitlementStatus.ACTIVE);

        // Step 4: Reply "Yes" to confirm revoke
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(convId)
                                .message("Yes")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PRO_MANAGEMENT"))
                .andExpect(jsonPath("$.updatedProUser.id").value(handleTargetUser.getId()))
                .andExpect(jsonPath("$.updatedProUser.adminGrantedPro").value(false))
                .andExpect(jsonPath("$.updatedProUser.membershipTier").value("FREE"));
    }

    @Test
    void revokeProFromPaidUserPreservesPaidSubscription() throws Exception {
        // First grant admin PRO on top of paid subscription
        entitlementRepository.save(MembershipEntitlement.builder()
                .userId(paidProUser.getId())
                .tier(MembershipTier.PRO)
                .grantSource(EntitlementGrantSource.ADMIN_GRANT)
                .status(EntitlementStatus.ACTIVE)
                .unlimited(true)
                .grantedByAdminId(adminUser.getId())
                .grantedAt(ZonedDateTime.now())
                .build());

        MvcResult step1 = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Revoke PRO from paid@example.com")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.pendingConfirmation.actionType").value("REVOKE_PRO"))
                .andExpect(jsonPath("$.pendingConfirmation.targetUserPaidPro").value(true))
                .andReturn();

        JsonNode body = objectMapper.readTree(step1.getResponse().getContentAsString());
        String token = body.get("pendingConfirmation").get("confirmationToken").asText();
        long convId = body.get("conversationId").asLong();

        mockMvc.perform(post("/api/v1/admin/ai/confirmations/{token}", token)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminConfirmationDecisionRequest.builder()
                                .approved(true)
                                .conversationId(convId)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.updatedUser.adminGrantedPro").value(false))
                .andExpect(jsonPath("$.updatedUser.paidPro").value(true))
                .andExpect(jsonPath("$.updatedUser.membershipTier").value("PRO"))
                .andExpect(jsonPath("$.updatedUser.membershipSource").value("Subscription"));
    }

    @Test
    void ambiguousOrUnknownTargetAsksForClarificationWithoutModifyingOrCreatingPendingAction() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Grant PRO to Alex")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PRO_MANAGEMENT"))
                .andExpect(jsonPath("$.pendingConfirmation").doesNotExist())
                .andExpect(jsonPath("$.updatedProUser").doesNotExist())
                .andExpect(jsonPath("$.proCandidates", hasSize(2)))
                .andExpect(jsonPath("$.assistantMessage.content", containsString("Clarification Needed")));

        assertThat(pendingActionRepository.findAll()).isEmpty();
        assertThat(entitlementRepository.findAll()).isEmpty();

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Grant PRO to nobody.missing@example.com")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("PRO_MANAGEMENT"))
                .andExpect(jsonPath("$.pendingConfirmation").doesNotExist())
                .andExpect(jsonPath("$.assistantMessage.content", containsString("No user account matched")));

        assertThat(pendingActionRepository.findAll()).isEmpty();
    }

    @Test
    void resolveTargetByNumericUserIdWorksWithConfirmationFlow() throws Exception {
        MvcResult step1 = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Grant PRO to user #" + freeTargetByEmail.getId())
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("CONFIRMATION_REQUIRED"))
                .andExpect(jsonPath("$.pendingConfirmation.targetUserId").value(freeTargetByEmail.getId()))
                .andReturn();

        JsonNode body = objectMapper.readTree(step1.getResponse().getContentAsString());
        String token = body.get("pendingConfirmation").get("confirmationToken").asText();
        long convId = body.get("conversationId").asLong();

        // Cancelling confirmation leaves user FREE
        mockMvc.perform(post("/api/v1/admin/ai/confirmations/{token}", token)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminConfirmationDecisionRequest.builder()
                                .approved(false)
                                .conversationId(convId)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(entitlementRepository.findByUserIdAndGrantSource(
                freeTargetByEmail.getId(), EntitlementGrantSource.ADMIN_GRANT)).isEmpty();
    }

    @Test
    void nonAdminCannotInvokeProAiCapabilitiesAndDirectConfirmedToolRequiresToken() throws Exception {
        // Non-admin rejected on all admin AI endpoints
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + nonAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Grant PRO to user@example.com")
                                .build())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + nonAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("grantPro")
                                .userIdentifier("user@example.com")
                                .confirmed(true)
                                .build())))
                .andExpect(status().isForbidden());

        // Direct service invocation as ROLE_USER throws AccessDeniedException
        authenticateAs(freeTargetByEmail);
        assertThrows(AccessDeniedException.class, () ->
                adminProToolService.executeTool(
                        freeTargetByEmail,
                        AdminToolName.GRANT_PRO,
                        AdminToolExecuteRequest.builder()
                                .toolName("grantPro")
                                .userId(freeTargetByEmail.getId())
                                .confirmed(false)
                                .build()));
        assertThrows(AccessDeniedException.class, () ->
                adminToolRouter.executeSingleTool(AdminToolExecuteRequest.builder()
                        .toolName("grantPro")
                        .userId(freeTargetByEmail.getId())
                        .confirmed(false)
                        .build()));
        SecurityContextHolder.clearContext();

        // Even as ADMIN, direct executeSingleTool with confirmed=true WITHOUT a confirmationToken MUST FAIL (400)
        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("grantPro")
                                .userId(freeTargetByEmail.getId())
                                .confirmed(true)
                                .build())))
                .andExpect(status().isBadRequest());

        assertThat(entitlementRepository.findByUserIdAndGrantSource(
                freeTargetByEmail.getId(), EntitlementGrantSource.ADMIN_GRANT)).isEmpty();
    }

    private void seedUsers() {
        adminUser = saveUser("admin.pro@test.local", "Admin Controller", Role.ROLE_ADMIN, MembershipTier.FREE);
        freeTargetByEmail = saveUser("user@example.com", "Target Email User", Role.ROLE_USER, MembershipTier.FREE);
        handleTargetUser = saveUser("handle.account@example.com", "Handle User Account", Role.ROLE_USER, MembershipTier.FREE);
        clientProfileRepository.save(ClientProfile.builder()
                .user(handleTargetUser)
                .displayName("user123")
                .build());
        paidProUser = saveUser("paid@example.com", "Paying Athlete", Role.ROLE_USER, MembershipTier.PRO);
        ambiguousAlexOne = saveUser("alex.mercer@example.com", "Alex Mercer", Role.ROLE_USER, MembershipTier.FREE);
        ambiguousAlexTwo = saveUser("alex.kim@example.com", "Alex Kim", Role.ROLE_USER, MembershipTier.FREE);

        adminToken = issueJwt(adminUser);
        nonAdminToken = issueJwt(freeTargetByEmail);
        targetEmailToken = issueJwt(freeTargetByEmail);
    }

    private User saveUser(String email, String fullName, Role role, MembershipTier tier) {
        return userRepository.save(User.builder()
                .email(email)
                .passwordHash("$2a$10$TestHashValue1234567890")
                .fullName(fullName)
                .role(role)
                .membershipTier(tier)
                .build());
    }

    private String issueJwt(User user) {
        return jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(user));
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private void cleanDb() {
        for (String table : List.of(
                "admin_ai_pending_actions",
                "admin_ai_audit_logs",
                "admin_ai_recommendations",
                "admin_ai_messages",
                "admin_ai_conversations",
                "admin_pro_audit_logs",
                "membership_entitlements",
                "getting_started_campaign_events",
                "getting_started_day_progress",
                "getting_started_day_completions",
                "shared_personal_records",
                "personal_records",
                "workout_logs",
                "workout_session_sets",
                "workout_session_exercises",
                "workout_sessions",
                "getting_started_enrollments",
                "getting_started_program_user_states",
                "getting_started_program_exercises",
                "getting_started_program_days",
                "getting_started_programs",
                "workout_plan_assignments",
                "workout_plan_exercises",
                "workout_plan_days",
                "workout_plans",
                "coach_client_relationships",
                "chat_messages",
                "chat_conversations",
                "user_presence",
                "ai_messages",
                "ai_conversations",
                "ai_workout_plan_logs",
                "coach_review_events",
                "coach_reviews",
                "user_report_evidence",
                "user_report_actions",
                "user_reports",
                "notifications",
                "rep_histories",
                "analysis_sessions",
                "analyses",
                "video_analysis_keypoints",
                "video_analyses",
                "video_analysis_quota_usages",
                "profile_media_items",
                "client_body_measurements",
                "client_profiles",
                "coach_profiles",
                "exercise_media_items",
                "exercises",
                "password_reset_otps",
                "registration_otps",
                "users"
        )) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }
}
