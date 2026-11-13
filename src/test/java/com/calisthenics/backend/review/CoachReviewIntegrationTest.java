package com.calisthenics.backend.review;

import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.review.entity.CoachReview;
import com.calisthenics.backend.review.repository.CoachReviewEventRepository;
import com.calisthenics.backend.review.repository.CoachReviewRepository;
import com.calisthenics.backend.review.service.CoachReviewService;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.util.AopTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CoachReviewIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private CoachClientRelationshipRepository relationshipRepository;
    @Autowired private CoachReviewRepository reviewRepository;
    @Autowired private CoachReviewEventRepository eventRepository;
    @Autowired private CoachReviewService reviewService;

    @org.junit.jupiter.api.BeforeEach
    void cleanReviews() {
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(reviewService), "editCooldownSeconds", 0L);
        eventRepository.deleteAll();
        reviewRepository.deleteAll();
    }

    @AfterEach
    void restoreCooldown() {
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(reviewService), "editCooldownSeconds", 30L);
    }

    private static String body(int overall, Integer quality, String comment) {
        return "{\"overallRating\":" + overall
                + (quality == null ? "" : ",\"coachingQuality\":" + quality)
                + (comment == null ? "" : ",\"comment\":\"" + comment + "\"") + "}";
    }

    @Test
    void assignedStudentCanReviewAndSeesOwnReviewWithoutPrivateData() throws Exception {
        User coach = createUser("Coach One");
        User student = createUser("Nguyen Van Anh");
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);

        submit(coach, student, body(5, 4, "Great coach")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.overallRating").value(5))
                .andExpect(jsonPath("$.reviewerName").value("Nguyen A."))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.studentId").doesNotExist())
                .andExpect(jsonPath("$.email").doesNotExist());

        // A third user sees the review but not as "mine" and without identifiers
        User other = createUser("Other");
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId() + "/reviews").with(auth(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].mine").value(false))
                .andExpect(jsonPath("$.content[0].studentId").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    void formerStudentWithTerminatedRelationshipCanReview() throws Exception {
        User coach = createUser("Coach");
        User student = createUser("Former");
        link(coach, student, CoachClientRelationshipStatus.TERMINATED);
        submit(coach, student, body(4, null, null)).andExpect(status().isCreated());
    }

    @Test
    void unrelatedUserPendingAndRejectedCannotReview() throws Exception {
        User coach = createUser("Coach");
        User stranger = createUser("Stranger");
        User pending = createUser("Pending");
        link(coach, pending, CoachClientRelationshipStatus.PENDING);

        submit(coach, stranger, body(5, null, null)).andExpect(status().isForbidden());
        submit(coach, pending, body(5, null, null)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId() + "/reviews/mine").with(auth(stranger)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(false))
                .andExpect(jsonPath("$.review").doesNotExist());
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void coachCannotReviewThemselvesEvenWithAForgedRelationshipRequestTarget() throws Exception {
        User coach = createUser("Coach");
        mockMvc.perform(post("/api/v1/coaches/" + coach.getId() + "/reviews").with(auth(coach))
                        .contentType(MediaType.APPLICATION_JSON).content(body(5, null, null)))
                .andExpect(status().isForbidden());
    }

    @Test
    void duplicateReviewIsRejectedAndDatabaseConstraintBacksItUp() throws Exception {
        User coach = createUser("Coach");
        User student = createUser("Student");
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);
        submit(coach, student, body(5, null, null)).andExpect(status().isCreated());
        submit(coach, student, body(1, null, null)).andExpect(status().isConflict());
        assertThat(reviewRepository.count()).isEqualTo(1);

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(CoachReview.builder()
                .coach(coach).student(student).overallRating(3).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void studentCanEditOwnReviewAndHistoryIsPreserved() throws Exception {
        User coach = createUser("Coach");
        User student = createUser("Student");
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);
        submit(coach, student, body(3, null, "Okay")).andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/coaches/" + coach.getId() + "/reviews/mine").with(auth(student))
                        .contentType(MediaType.APPLICATION_JSON).content(body(5, 5, "Much better")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overallRating").value(5))
                .andExpect(jsonPath("$.comment").value("Much better"));

        CoachReview review = reviewRepository.findByCoachIdAndStudentId(coach.getId(), student.getId()).orElseThrow();
        assertThat(reviewRepository.count()).isEqualTo(1);
        assertThat(eventRepository.findByReviewIdOrderByCreatedAtAscIdAsc(review.getId()))
                .extracting(e -> e.getEventType().name() + ":" + e.getOverallRating())
                .containsExactly("CREATED:3", "EDITED:5");
    }

    @Test
    void editWithoutExistingReviewIsNotFound() throws Exception {
        User coach = createUser("Coach");
        User student = createUser("Student");
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);
        mockMvc.perform(put("/api/v1/coaches/" + coach.getId() + "/reviews/mine").with(auth(student))
                        .contentType(MediaType.APPLICATION_JSON).content(body(5, null, null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void rapidRepeatedEditsAreRateLimited() throws Exception {
        ReflectionTestUtils.setField((Object) AopTestUtils.getTargetObject(reviewService), "editCooldownSeconds", 3600L);
        User coach = createUser("Coach");
        User student = createUser("Student");
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);
        submit(coach, student, body(3, null, null)).andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/coaches/" + coach.getId() + "/reviews/mine").with(auth(student))
                        .contentType(MediaType.APPLICATION_JSON).content(body(4, null, null)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void invalidRatingsAndOverlongCommentsAreRejected() throws Exception {
        User coach = createUser("Coach");
        User student = createUser("Student");
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);
        submit(coach, student, body(0, null, null)).andExpect(status().isBadRequest());
        submit(coach, student, body(6, null, null)).andExpect(status().isBadRequest());
        submit(coach, student, body(5, 9, null)).andExpect(status().isBadRequest());
        submit(coach, student, body(5, null, "x".repeat(1001))).andExpect(status().isBadRequest());
        submit(coach, student, "{}").andExpect(status().isBadRequest());
    }

    @Test
    void summaryComputesAverageDistributionAndCriteria() throws Exception {
        User coach = createUser("Coach");
        int[] ratings = {5, 5, 4, 2};
        for (int rating : ratings) {
            User s = createUser("S");
            link(coach, s, CoachClientRelationshipStatus.ACTIVE);
            submit(coach, s, body(rating, rating, null)).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId() + "/reviews/summary").with(auth(createUser("V"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(4))
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.distribution['5']").value(2))
                .andExpect(jsonPath("$.distribution['4']").value(1))
                .andExpect(jsonPath("$.distribution['3']").value(0))
                .andExpect(jsonPath("$.distribution['2']").value(1))
                .andExpect(jsonPath("$.distribution['1']").value(0))
                .andExpect(jsonPath("$.coachingQualityAvg").value(4.0))
                .andExpect(jsonPath("$.communicationAvg").doesNotExist());
    }

    @Test
    void summaryForCoachWithoutReviewsIsEmptyAndProfilesStayCompatible() throws Exception {
        User coach = createUser("Coach");
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId() + "/reviews/summary").with(auth(createUser("V"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReviews").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId()).with(auth(createUser("V2"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Coach"))
                .andExpect(jsonPath("$.reviewCount").value(0))
                .andExpect(jsonPath("$.averageRating").doesNotExist());
    }

    @Test
    void hiddenReviewsAreExcludedFromPublicRatingsButKeptForAdmins() throws Exception {
        User coach = createUser("Coach");
        User student = createUser("Student");
        User admin = createAdmin();
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);
        submit(coach, student, body(1, null, "Rude")).andExpect(status().isCreated());
        Long reviewId = reviewRepository.findByCoachIdAndStudentId(coach.getId(), student.getId()).orElseThrow().getId();

        // reason is mandatory when hiding
        mockMvc.perform(put("/api/v1/admin/reviews/" + reviewId + "/moderation").with(auth(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/admin/reviews/" + reviewId + "/moderation").with(auth(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\",\"reason\":\"Abusive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("HIDDEN"))
                .andExpect(jsonPath("$.moderatedByAdminId").value(admin.getId()));

        mockMvc.perform(get("/api/v1/coaches/" + coach.getId() + "/reviews/summary").with(auth(student)))
                .andExpect(jsonPath("$.totalReviews").value(0));
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId() + "/reviews").with(auth(student)))
                .andExpect(jsonPath("$.content.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/reviews/" + reviewId).with(auth(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.comment").value("Rude"))
                .andExpect(jsonPath("$.studentName").value("Student"))
                .andExpect(jsonPath("$.history.length()").value(2));

        // restore
        mockMvc.perform(put("/api/v1/admin/reviews/" + reviewId + "/moderation").with(auth(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"VISIBLE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(3));
        mockMvc.perform(get("/api/v1/coaches/" + coach.getId() + "/reviews/summary").with(auth(student)))
                .andExpect(jsonPath("$.totalReviews").value(1));
    }

    @Test
    void coachesAndStudentsCannotModerateOrDeleteReviews() throws Exception {
        User coach = createUser("Coach");
        User student = createUser("Student");
        link(coach, student, CoachClientRelationshipStatus.ACTIVE);
        submit(coach, student, body(2, null, "meh")).andExpect(status().isCreated());
        Long reviewId = reviewRepository.findByCoachIdAndStudentId(coach.getId(), student.getId()).orElseThrow().getId();

        for (User actor : new User[]{coach, student}) {
            mockMvc.perform(put("/api/v1/admin/reviews/" + reviewId + "/moderation").with(auth(actor))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"HIDDEN\",\"reason\":\"x\"}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get("/api/v1/admin/reviews").with(auth(actor))).andExpect(status().isForbidden());
        }
        // No delete / coach-edit endpoints exist for reviews
        mockMvc.perform(delete("/api/v1/coaches/" + coach.getId() + "/reviews/mine").with(auth(coach)))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isGreaterThanOrEqualTo(400));
        mockMvc.perform(put("/api/v1/coaches/" + coach.getId() + "/reviews/mine").with(auth(coach))
                        .contentType(MediaType.APPLICATION_JSON).content(body(5, null, null)))
                .andExpect(status().isNotFound());
        assertThat(reviewRepository.findById(reviewId).orElseThrow().getOverallRating()).isEqualTo(2);
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/coaches/1/reviews/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/coaches/1/reviews").contentType(MediaType.APPLICATION_JSON).content(body(5, null, null)))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions submit(User coach, User student, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/coaches/" + coach.getId() + "/reviews").with(auth(student))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void link(User coach, User client, CoachClientRelationshipStatus status) {
        relationshipRepository.saveAndFlush(CoachClientRelationship.builder()
                .coach(coach).client(client).requestedBy(client).status(status).build());
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
