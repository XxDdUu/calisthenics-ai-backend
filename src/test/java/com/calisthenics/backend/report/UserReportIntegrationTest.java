package com.calisthenics.backend.report;

import com.calisthenics.backend.report.entity.UserReport;
import com.calisthenics.backend.report.entity.UserReportStatus;
import com.calisthenics.backend.report.repository.UserReportActionRepository;
import com.calisthenics.backend.report.repository.UserReportRepository;
import com.calisthenics.backend.report.entity.ReportReason;
import com.calisthenics.backend.report.service.UserReportService;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserReportIntegrationTest {

    private static final String EVIDENCE = "https://api.example.com/api/v1/storage/files/abc.png";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private UserReportRepository reportRepository;
    @Autowired private UserReportActionRepository actionRepository;
    @Autowired private UserReportService reportService;

    @org.junit.jupiter.api.BeforeEach
    void cleanReports() {
        actionRepository.deleteAll();
        reportRepository.deleteAll();
    }

    @AfterEach
    void resetLimit() {
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(reportService), "maxReportsPerDay", 10);
    }

    @Test
    void anyUserCanReportAnyOtherUserWithoutExposingReporterIdentity() throws Exception {
        User a = createUser("Alice");
        User b = createUser("Bob");
        // student->coach, coach->student, coach->coach, student->student are all plain user-to-user reports
        report(a, b, "HARASSMENT", "He insulted me", EVIDENCE).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.reporter").doesNotExist())
                .andExpect(jsonPath("$.reportedUser").doesNotExist());
        report(b, a, "SPAM_OR_SCAM", null, null).andExpect(status().isCreated());

        UserReport stored = reportRepository.findAll().stream()
                .filter(r -> r.getReporter().getId().equals(a.getId())).findFirst().orElseThrow();
        assertThat(stored.getReportedUser().getId()).isEqualTo(b.getId());
        assertThat(stored.getStatus()).isEqualTo(UserReportStatus.PENDING);
    }

    @Test
    void usersCannotReportThemselvesOrMissingUsers() throws Exception {
        User a = createUser("Alice");
        report(a, a, "OTHER", null, null).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/users/999999/reports").with(auth(a))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateActiveReportIsRejectedButDifferentReporterOrReasonIsAllowed() throws Exception {
        User a = createUser("Alice");
        User c = createUser("Carol");
        User b = createUser("Bob");
        report(a, b, "FRAUD", null, null).andExpect(status().isCreated());
        report(a, b, "FRAUD", "again", null).andExpect(status().isConflict());
        report(a, b, "HARASSMENT", null, null).andExpect(status().isCreated());   // different incident type
        report(c, b, "FRAUD", null, null).andExpect(status().isCreated());        // different reporter
        assertThat(reportRepository.count()).isEqualTo(3);
    }

    @Test
    void reportCanBeFiledAgainAfterPreviousOneIsClosed() throws Exception {
        User a = createUser("Alice");
        User b = createUser("Bob");
        User admin = createAdmin();
        report(a, b, "OTHER", null, null).andExpect(status().isCreated());
        Long id = reportRepository.findAll().get(0).getId();
        adminPut(admin, id, "{\"status\":\"DISMISSED\"}").andExpect(status().isOk());
        report(a, b, "OTHER", null, null).andExpect(status().isCreated());
    }

    @Test
    void validationRejectsBadReasonOverlongTextAndForeignEvidence() throws Exception {
        User a = createUser("Alice");
        User b = createUser("Bob");
        mockMvc.perform(post("/api/v1/users/" + b.getId() + "/reports").with(auth(a))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"NOPE\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/users/" + b.getId() + "/reports").with(auth(a))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        report(a, b, "OTHER", "x".repeat(2001), null).andExpect(status().isBadRequest());
        report(a, b, "OTHER", null, "https://evil.example.com/tracker.png").andExpect(status().isBadRequest());
        assertThat(reportRepository.count()).isZero();
    }

    @Test
    void reportersAreRateLimited() throws Exception {
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(reportService), "maxReportsPerDay", 2);
        User a = createUser("Alice");
        report(a, createUser("B1"), "OTHER", null, null).andExpect(status().isCreated());
        report(a, createUser("B2"), "OTHER", null, null).andExpect(status().isCreated());
        report(a, createUser("B3"), "OTHER", null, null).andExpect(status().isTooManyRequests());
    }

    @Test
    void reportedUserAndOtherUsersCannotSeeReportsOrUseModerationTools() throws Exception {
        User a = createUser("Alice");
        User b = createUser("Bob");
        report(a, b, "HARASSMENT", "details", EVIDENCE).andExpect(status().isCreated());
        Long id = reportRepository.findAll().get(0).getId();

        for (User u : new User[]{a, b, createUser("Other")}) {
            mockMvc.perform(get("/api/v1/admin/user-reports").with(auth(u))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/user-reports/summary").with(auth(u))).andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/user-reports/" + id).with(auth(u))).andExpect(status().isForbidden());
            adminPut(u, id, "{\"status\":\"RESOLVED\"}").andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/user-reports/" + id + "/notes").with(auth(u))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"x\"}")).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/v1/admin/user-reports/" + id + "/actions").with(auth(u))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"WARNING\"}")).andExpect(status().isForbidden());
            // no user-facing read endpoint for reports exists
            mockMvc.perform(get("/api/v1/users/" + b.getId() + "/reports").with(auth(u))).andExpect(r -> assertThat(r.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
        }
        mockMvc.perform(get("/api/v1/admin/user-reports")).andExpect(status().isUnauthorized());
        assertThat(reportRepository.findById(id).orElseThrow().getStatus()).isEqualTo(UserReportStatus.PENDING);
    }

    @Test
    void submittingAReportDoesNotChangeTheReportedAccount() throws Exception {
        User a = createUser("Alice");
        User b = createUser("Bob");
        report(a, b, "HARASSMENT", null, null).andExpect(status().isCreated());
        User reloaded = userRepository.findById(b.getId()).orElseThrow();
        assertThat(reloaded.getRole()).isEqualTo(Role.ROLE_USER);
        assertThat(reloaded.getEmail()).isEqualTo(b.getEmail());
    }

    @Test
    void adminSeesCountsDetailsEvidenceAndPreviousReports() throws Exception {
        User a = createUser("Alice");
        User c = createUser("Carol");
        User b = createUser("Bob");
        User admin = createAdmin();
        report(a, b, "HARASSMENT", "first", EVIDENCE).andExpect(status().isCreated());
        report(c, b, "FRAUD", "second", null).andExpect(status().isCreated());
        Long first = reportRepository.findAll().stream().filter(r -> "first".equals(r.getDescription()))
                .findFirst().orElseThrow().getId();

        mockMvc.perform(get("/api/v1/admin/user-reports/summary").with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.pending").value(2))
                .andExpect(jsonPath("$.underReview").value(0));
        mockMvc.perform(get("/api/v1/admin/user-reports?status=PENDING&reportedUserId=" + b.getId()).with(auth(admin)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get("/api/v1/admin/user-reports/" + first).with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reporter.fullName").value("Alice"))
                .andExpect(jsonPath("$.reporter.email").value(a.getEmail()))
                .andExpect(jsonPath("$.reportedUser.fullName").value("Bob"))
                .andExpect(jsonPath("$.evidenceUrls[0]").value(EVIDENCE))
                .andExpect(jsonPath("$.description").value("first"))
                .andExpect(jsonPath("$.previousReports.length()").value(1))
                .andExpect(jsonPath("$.previousReports[0].reason").value("FRAUD"));
    }

    @Test
    void moderationWorkflowRecordsAdminTimestampsNotesAndActions() throws Exception {
        User a = createUser("Alice");
        User b = createUser("Bob");
        User admin = createAdmin();
        report(a, b, "ABUSIVE_LANGUAGE", "bad words", null).andExpect(status().isCreated());
        Long id = reportRepository.findAll().get(0).getId();

        adminPut(admin, id, "{\"status\":\"UNDER_REVIEW\",\"note\":\"Looking into it\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNDER_REVIEW"))
                .andExpect(jsonPath("$.handledByAdminId").value(admin.getId()))
                .andExpect(jsonPath("$.reviewedAt").exists())
                .andExpect(jsonPath("$.resolvedAt").doesNotExist());

        mockMvc.perform(post("/api/v1/admin/user-reports/" + id + "/notes").with(auth(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Internal: check chat logs\"}"))
                .andExpect(status().isOk());

        // validation of moderation actions
        action(admin, id, "{\"action\":\"TEMPORARY_SUSPENSION\"}").andExpect(status().isBadRequest());
        action(admin, id, "{\"action\":\"WARNING\",\"suspendedUntil\":\"" + Instant.now().plus(Duration.ofDays(1)) + "\"}")
                .andExpect(status().isBadRequest());
        action(admin, id, "{\"action\":\"TEMPORARY_SUSPENSION\",\"suspendedUntil\":\"" + Instant.now().minus(Duration.ofDays(1)) + "\"}")
                .andExpect(status().isBadRequest());
        action(admin, id, "{\"action\":\"WARNING\",\"note\":\"Formal warning\"}").andExpect(status().isOk());

        adminPut(admin, id, "{\"status\":\"RESOLVED\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").exists())
                .andExpect(jsonPath("$.actions.length()").value(4))
                .andExpect(jsonPath("$.actions[0].type").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$.actions[0].adminName").value("Admin"))
                .andExpect(jsonPath("$.actions[1].type").value("NOTE"))
                .andExpect(jsonPath("$.actions[2].moderationAction").value("WARNING"));

        // finished reports are terminal
        adminPut(admin, id, "{\"status\":\"UNDER_REVIEW\"}").andExpect(status().isConflict());
        assertThat(actionRepository.findByReportIdOrderByCreatedAtAscIdAsc(id)).hasSize(4)
                .allSatisfy(x -> assertThat(x.getAdminId()).isEqualTo(admin.getId()));

        // no automatic punishment: the reported account is untouched
        assertThat(userRepository.findById(b.getId()).orElseThrow().getRole()).isEqualTo(Role.ROLE_USER);
    }

    @Test
    void dismissedReportsRejectActionsAndSuspensionNeedsFutureDate() throws Exception {
        User a = createUser("Alice");
        User b = createUser("Bob");
        User admin = createAdmin();
        report(a, b, "OTHER", null, null).andExpect(status().isCreated());
        Long id = reportRepository.findAll().get(0).getId();
        action(admin, id, "{\"action\":\"TEMPORARY_SUSPENSION\",\"suspendedUntil\":\"" + Instant.now().plus(Duration.ofDays(7)) + "\"}")
                .andExpect(status().isOk());
        adminPut(admin, id, "{\"status\":\"DISMISSED\"}").andExpect(status().isOk());
        action(admin, id, "{\"action\":\"PERMANENT_BAN\"}").andExpect(status().isConflict());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions report(User reporter, User reported, String reason, String description, String evidence) throws Exception {
        StringBuilder json = new StringBuilder("{\"reason\":\"" + reason + "\"");
        if (description != null) json.append(",\"description\":\"").append(description).append("\"");
        if (evidence != null) json.append(",\"evidenceUrls\":[\"").append(evidence).append("\"]");
        json.append("}");
        return mockMvc.perform(post("/api/v1/users/" + reported.getId() + "/reports").with(auth(reporter))
                .contentType(MediaType.APPLICATION_JSON).content(json.toString()));
    }

    private ResultActions adminPut(User actor, Long id, String json) throws Exception {
        return mockMvc.perform(put("/api/v1/admin/user-reports/" + id + "/status").with(auth(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions action(User actor, Long id, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/admin/user-reports/" + id + "/actions").with(auth(actor))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private User createUser(String name) {
        return userRepository.saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@example.com").fullName(name).role(Role.ROLE_USER).build());
    }

    private User createAdmin() {
        return userRepository.saveAndFlush(User.builder()
                .email(UUID.randomUUID() + "@example.com").fullName("Admin").role(Role.ROLE_ADMIN).build());
    }

    private RequestPostProcessor auth(User user) {
        return user(UserPrincipal.create(user));
    }
}
