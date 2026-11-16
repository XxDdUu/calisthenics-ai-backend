package com.calisthenics.backend.notification;

import com.calisthenics.backend.notification.model.Notification;
import com.calisthenics.backend.notification.model.NotificationType;
import com.calisthenics.backend.notification.repository.NotificationRepository;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationSystemIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private ClientProfileRepository clientProfileRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private NotificationRepository notificationRepository;

    private final List<Long> createdUserIds = new ArrayList<>();

    @BeforeEach
    void cleanGettingStartedBeforeTest() {
        cleanGettingStartedTables();
    }

    @AfterEach
    void cleanupTestArtifacts() {
        cleanGettingStartedTables();
        if (!createdUserIds.isEmpty()) {
            String inClause = String.join(",", createdUserIds.stream().map(String::valueOf).toList());
            jdbcTemplate.update("DELETE FROM notifications WHERE recipient_id IN (" + inClause + ") OR sender_id IN (" + inClause + ")");
            jdbcTemplate.update("DELETE FROM workout_plan_assignments WHERE coach_id IN (" + inClause + ") OR client_id IN (" + inClause + ")");
            jdbcTemplate.update("DELETE FROM coach_client_relationships WHERE coach_id IN (" + inClause + ") OR client_id IN (" + inClause + ")");
            workoutPlanRepository.deleteAll(
                    workoutPlanRepository.findAll().stream()
                            .filter(p -> p.getUser() != null && createdUserIds.contains(p.getUser().getId()))
                            .toList()
            );
            workoutPlanRepository.flush();
            jdbcTemplate.update("DELETE FROM coach_profiles WHERE user_id IN (" + inClause + ")");
            jdbcTemplate.update("DELETE FROM client_profiles WHERE user_id IN (" + inClause + ")");
            jdbcTemplate.update("DELETE FROM users WHERE id IN (" + inClause + ")");
            createdUserIds.clear();
        }
    }

    private void cleanGettingStartedTables() {
        jdbcTemplate.update("DELETE FROM getting_started_day_progress");
        jdbcTemplate.update("DELETE FROM getting_started_day_completions");
        jdbcTemplate.update("DELETE FROM getting_started_enrollments");
        jdbcTemplate.update("DELETE FROM getting_started_program_user_states");
        jdbcTemplate.update("DELETE FROM getting_started_campaign_events");
        jdbcTemplate.update("DELETE FROM getting_started_program_exercises");
        jdbcTemplate.update("DELETE FROM getting_started_program_days");
        jdbcTemplate.update("DELETE FROM getting_started_programs");
    }

    @Test
    void coachAssignWorkout_createsWorkoutAssignedNotificationForClient_andDeduplicatesRetries() throws Exception {
        User coach = createUser("Coach Alpha", Role.ROLE_USER);
        User client = createUser("Client Beta", Role.ROLE_USER);
        User bystander = createUser("Client Gamma", Role.ROLE_USER);
        WorkoutPlan plan = createPlan(coach, "Hypertrophy Push Pull");

        long relationshipId = connectUsers(coach, client);
        notificationRepository.deleteAll(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(client.getId()));
        notificationRepository.deleteAll(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(coach.getId()));

        String assignmentPayload = objectMapper.writeValueAsString(Map.of(
                "workoutPlanId", plan.getId(),
                "startDate", LocalDate.now().toString(),
                "endDate", LocalDate.now().plusDays(14).toString(),
                "notes", "Focus on strict tempo"
        ));

        mockMvc.perform(post("/api/v1/coach-client-relationships/{relationshipId}/assignments", relationshipId)
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload))
                .andExpect(status().isCreated());

        // Retry identical assignment payload -> must not duplicate the notification
        mockMvc.perform(post("/api/v1/coach-client-relationships/{relationshipId}/assignments", relationshipId)
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(assignmentPayload))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notifications")
                        .with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("WORKOUT_ASSIGNED"))
                .andExpect(jsonPath("$[0].title").value("New Workout Assigned"))
                .andExpect(jsonPath("$[0].relatedEntityType").value("WORKOUT_PLAN"))
                .andExpect(jsonPath("$[0].relatedEntityId").value(plan.getId()))
                .andExpect(jsonPath("$[0].deepLink").value("workout_plan_details/" + plan.getId()))
                .andExpect(jsonPath("$[0].isRead").value(false));

        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(bystander.getId())).isEmpty();
    }

    @Test
    void userCannotReadOrModifyAnotherUsersNotifications() throws Exception {
        User coach = createUser("Coach Access", Role.ROLE_USER);
        User clientA = createUser("Client A", Role.ROLE_USER);
        User clientB = createUser("Client B", Role.ROLE_USER);
        WorkoutPlan plan = createPlan(coach, "Client A Plan");

        long relId = connectUsers(coach, clientA);
        notificationRepository.deleteAll(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(clientA.getId()));

        mockMvc.perform(post("/api/v1/coach-client-relationships/{relationshipId}/assignments", relId)
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workoutPlanId\":" + plan.getId() + "}"))
                .andExpect(status().isCreated());

        List<Notification> clientANotifications = notificationRepository.findByRecipientIdOrderByCreatedAtDesc(clientA.getId());
        assertThat(clientANotifications).hasSize(1);
        long clientANotificationId = clientANotifications.get(0).getId();

        // Client B receives empty notification list and gets 403 on Client A's notification
        mockMvc.perform(get("/api/v1/notifications").with(authenticatedAs(clientB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/notifications/{id}", clientANotificationId).with(authenticatedAs(clientB)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", clientANotificationId).with(authenticatedAs(clientB)))
                .andExpect(status().isForbidden());

        assertThat(notificationRepository.findById(clientANotificationId).orElseThrow().isRead()).isFalse();
    }

    @Test
    void adminPublishProgram_notifiesEligibleUsersOnly_andOpeningProgramDoesNotDuplicate() throws Exception {
        User admin = createUser("Platform Admin", Role.ROLE_ADMIN);
        User recentAthlete = createUser("Recent Athlete", Role.ROLE_USER);
        User veteranAthlete = createUser("Veteran Athlete", Role.ROLE_USER);

        // Push veteran athlete's createdAt 25 days into the past so LAST_14_DAYS excludes them
        jdbcTemplate.update(
                "UPDATE users SET created_at = ? WHERE id = ?",
                ZonedDateTime.now().minusDays(25).toOffsetDateTime(),
                veteranAthlete.getId()
        );

        String programBody = buildThreeDayProgramJson("3 Days Getting Started", "LAST_14_DAYS");

        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/getting-started-program")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(programBody))
                .andExpect(status().isCreated())
                .andReturn();
        long programId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/admin/getting-started-program/publish")
                        .with(authenticatedAs(admin)))
                .andExpect(status().isOk());

        // Republishing the same program version must not duplicate notifications
        mockMvc.perform(post("/api/v1/admin/getting-started-program/publish")
                        .with(authenticatedAs(admin)))
                .andExpect(status().isOk());

        // Opening the program repeatedly must not generate extra notifications
        mockMvc.perform(get("/api/v1/getting-started-program")
                        .with(authenticatedAs(recentAthlete)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/getting-started-program")
                        .with(authenticatedAs(recentAthlete)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications").with(authenticatedAs(recentAthlete)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("PROGRAM_AVAILABLE"))
                .andExpect(jsonPath("$[0].relatedEntityType").value("GETTING_STARTED_PROGRAM"))
                .andExpect(jsonPath("$[0].relatedEntityId").value(programId))
                .andExpect(jsonPath("$[0].deepLink").value("getting_started/" + programId));

        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(veteranAthlete.getId())).isEmpty();
        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(admin.getId())).isEmpty();
    }

    @Test
    void adminAnnouncement_requiresAdminRole_targetsSelectedAudience_andDeduplicatesRetries() throws Exception {
        User admin = createUser("Admin User", Role.ROLE_ADMIN);
        User coach = createUser("Coach User", Role.ROLE_USER);
        User client = createUser("Client User", Role.ROLE_USER);
        coachProfileRepository.saveAndFlush(CoachProfile.builder().user(coach).displayName("Coach").build());
        clientProfileRepository.saveAndFlush(ClientProfile.builder().user(client).displayName("Client").build());

        String requestBody = objectMapper.writeValueAsString(Map.of(
                "title", "Platform Announcement",
                "message", "New platform updates are available.",
                "targetRole", "COACH",
                "eventKey", "release-2026-11"
        ));

        // Regular user cannot create announcements
        mockMvc.perform(post("/api/v1/admin/announcements")
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .with(authenticatedAs(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());

        // Admin creates announcement twice with same eventKey -> created only once
        mockMvc.perform(post("/api/v1/admin/announcements")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .with(authenticatedAs(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recipientCount").value(0));

        mockMvc.perform(get("/api/v1/notifications").with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("ADMIN_ANNOUNCEMENT"))
                .andExpect(jsonPath("$[0].title").value("Platform Announcement"))
                .andExpect(jsonPath("$[0].relatedEntityType").value("ANNOUNCEMENT"));

        assertThat(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(client.getId())).isEmpty();
    }

    @Test
    void coachClientRequests_acceptAndReject_sendNotificationsToCorrectParticipants() throws Exception {
        User coach = createUser("Coach Prime", Role.ROLE_USER);
        User client1 = createUser("Athlete One", Role.ROLE_USER);
        User client2 = createUser("Athlete Two", Role.ROLE_USER);

        // Client 1 sends request -> Coach receives COACH_CLIENT_REQUEST
        MvcResult req1 = mockMvc.perform(post("/api/v1/coach-client-relationships/requests")
                        .with(authenticatedAs(client1))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":" + coach.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        long rel1Id = objectMapper.readTree(req1.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(get("/api/v1/notifications").with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("COACH_CLIENT_REQUEST"))
                .andExpect(jsonPath("$[0].relatedEntityId").value(rel1Id));

        // Coach accepts Client 1 -> Client 1 receives acceptance notification
        mockMvc.perform(post("/api/v1/coach-client-relationships/{id}/accept", rel1Id)
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications").with(authenticatedAs(client1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("COACH_CLIENT_REQUEST"))
                .andExpect(jsonPath("$[0].title").value("Coaching Request Accepted"))
                .andExpect(jsonPath("$[0].deepLink").value("coach"));

        // Client 2 sends request -> Coach rejects -> Client 2 receives decline notification
        MvcResult req2 = mockMvc.perform(post("/api/v1/coach-client-relationships/requests")
                        .with(authenticatedAs(client2))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":" + coach.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        long rel2Id = objectMapper.readTree(req2.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/coach-client-relationships/{id}/reject", rel2Id)
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications").with(authenticatedAs(client2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("COACH_CLIENT_REQUEST"))
                .andExpect(jsonPath("$[0].title").value("Coaching Request Declined"));
    }

    @Test
    void pagination_markOneAsRead_andMarkAllAsRead_updateUnreadCountAndReadState() throws Exception {
        User admin = createUser("Admin Broadcast", Role.ROLE_ADMIN);
        User reader = createUser("Reader User", Role.ROLE_USER);

        for (int i = 1; i <= 3; i++) {
            mockMvc.perform(post("/api/v1/admin/announcements")
                            .with(authenticatedAs(admin))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "title", "Update " + i,
                                    "message", "Message " + i,
                                    "recipientUserIds", List.of(reader.getId()),
                                    "eventKey", "page-test-" + i
                            ))))
                    .andExpect(status().isCreated());
        }

        // Unread count should be 3
        mockMvc.perform(get("/api/v1/notifications/unread-count").with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));

        // Page 0 size 2 returns 2 items, page 1 size 2 returns 1 item
        MvcResult page0Result = mockMvc.perform(get("/api/v1/notifications?page=0&size=2")
                        .with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andReturn();
        JsonNode page0Json = objectMapper.readTree(page0Result.getResponse().getContentAsString());
        long firstId = page0Json.get(0).get("id").asLong();

        mockMvc.perform(get("/api/v1/notifications?page=1&size=2").with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));

        mockMvc.perform(get("/api/v1/notifications/paged?page=0&size=2").with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalElements").value(3));

        // Mark one notification as read -> unread count drops to 2 and readAt is populated
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", firstId).with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isRead").value(true))
                .andExpect(jsonPath("$.readAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(2));

        // Unread filter returns only the remaining 2
        mockMvc.perform(get("/api/v1/notifications?unreadOnly=true").with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Mark all as read -> unread count becomes 0
        mockMvc.perform(patch("/api/v1/notifications/read-all").with(authenticatedAs(reader)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/unread-count").with(authenticatedAs(reader)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    private long connectUsers(User coach, User client) throws Exception {
        MvcResult requestRes = mockMvc.perform(post("/api/v1/coach-client-relationships/requests")
                        .with(authenticatedAs(client))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coachId\":" + coach.getId() + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        long relationshipId = objectMapper.readTree(requestRes.getResponse().getContentAsString()).get("id").asLong();

        mockMvc.perform(post("/api/v1/coach-client-relationships/{id}/accept", relationshipId)
                        .with(authenticatedAs(coach)))
                .andExpect(status().isOk());
        return relationshipId;
    }

    private WorkoutPlan createPlan(User owner, String name) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name(name)
                .splitType(WorkoutSplitType.PPL)
                .build();
        for (WorkoutDayOfWeek day : WorkoutDayOfWeek.values()) {
            plan.getDays().add(WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(day)
                    .sessionType(WorkoutSessionType.REST)
                    .build());
        }
        return workoutPlanRepository.saveAndFlush(plan);
    }

    private User createUser(String name, Role role) {
        User saved = userRepository.saveAndFlush(User.builder()
                .email("notif-" + UUID.randomUUID() + "@test.com")
                .fullName(name)
                .role(role)
                .build());
        createdUserIds.add(saved.getId());
        return saved;
    }

    private RequestPostProcessor authenticatedAs(User user) {
        return user(UserPrincipal.create(user));
    }

    private String buildThreeDayProgramJson(String title, String targetAudience) throws Exception {
        List<Map<String, Object>> days = new ArrayList<>();
        for (int day = 1; day <= 3; day++) {
            days.add(Map.of(
                    "dayNumber", day,
                    "title", "Day " + day,
                    "description", "Foundations day " + day,
                    "exercises", List.of(Map.of(
                            "exerciseName", "Push Ups",
                            "sets", 3,
                            "reps", 10,
                            "restSeconds", 60,
                            "displayOrder", 0
                    ))
            ));
        }
        return objectMapper.writeValueAsString(Map.of(
                "title", title,
                "description", "3-day foundation",
                "targetAudience", targetAudience,
                "days", days
        ));
    }
}
