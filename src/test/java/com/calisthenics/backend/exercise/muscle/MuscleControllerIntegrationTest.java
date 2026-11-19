package com.calisthenics.backend.exercise.muscle;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MuscleControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Test
    void publicMuscleEndpointsReturnAll22CanonicalMuscles() throws Exception {
        mockMvc.perform(get("/api/v1/muscles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(22))
                .andExpect(jsonPath("$[0].id").value("chest"))
                .andExpect(jsonPath("$[0].svgElementIds[0]").value("chest_left"))
                .andExpect(jsonPath("$[21].id").value("calves"));

        mockMvc.perform(get("/api/v1/exercises/muscles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(22))
                .andExpect(jsonPath("$[1].id").value("front_deltoids"));
    }

    @Test
    void adminCreateAndUpdateExerciseWithCanonicalMusclesPersistsAndRejectsInvalidIds() throws Exception {
        User admin = userRepository.save(User.builder()
                .email("muscle_admin@calisthenics.ai")
                .fullName("Muscle Admin")
                .role(Role.ROLE_ADMIN)
                .build());
        String adminToken = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(admin));

        mockMvc.perform(post("/api/v1/admin/exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pseudo Planche Push-Up",
                                  "code": "PSEUDO_PLANCHE_PUSH_UP",
                                  "category": "PUSH",
                                  "difficulty": "INTERMEDIATE",
                                  "primaryMuscles": ["front_deltoids", "chest"],
                                  "secondaryMuscleIds": ["triceps", "rectus_abdominis"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.primaryMuscles[0]").value("front_deltoids"))
                .andExpect(jsonPath("$.primaryMuscleIds[1]").value("chest"))
                .andExpect(jsonPath("$.secondaryMuscleIds[0]").value("triceps"))
                .andExpect(jsonPath("$.targetMuscles").value("Front Deltoids, Chest"))
                .andExpect(jsonPath("$.muscleTargets.length()").value(4));

        Exercise persisted = exerciseRepository.findByCode("PSEUDO_PLANCHE_PUSH_UP").orElseThrow();
        assertThat(persisted.getMuscleTargetMappings()).hasSize(4);

        // Public GET exercise detail returns canonical muscle targets
        mockMvc.perform(get("/api/v1/exercises/" + persisted.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMuscleIds[0]").value("front_deltoids"))
                .andExpect(jsonPath("$.secondaryMuscleIds[1]").value("rectus_abdominis"))
                .andExpect(jsonPath("$.muscleTargets.length()").value(4));

        // Update swaps muscle roles cleanly
        mockMvc.perform(put("/api/v1/admin/exercises/" + persisted.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Pseudo Planche Push-Up",
                                  "code": "PSEUDO_PLANCHE_PUSH_UP",
                                  "category": "PUSH",
                                  "difficulty": "INTERMEDIATE",
                                  "primaryMuscles": ["chest"],
                                  "secondaryMuscleIds": ["front_deltoids", "triceps"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryMuscleIds.length()").value(1))
                .andExpect(jsonPath("$.primaryMuscleIds[0]").value("chest"))
                .andExpect(jsonPath("$.secondaryMuscleIds.length()").value(2));

        // Invalid canonical ID fails with 400
        mockMvc.perform(post("/api/v1/admin/exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Invalid Muscle Exercise",
                                  "code": "INVALID_MUSCLE_EX",
                                  "category": "PUSH",
                                  "difficulty": "BEGINNER",
                                  "primaryMuscles": ["not_a_real_muscle"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
