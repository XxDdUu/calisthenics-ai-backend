package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminExerciseDeleteResponse;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.admin.dto.CreateAdminExerciseRequest;
import com.calisthenics.backend.admin.dto.UpdateAdminExerciseRequest;
import com.calisthenics.backend.analysis.AnalysisRepository;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.workout.WorkoutSessionExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AdminExerciseService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "category", "difficulty", "createdAt", "active");

    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final WorkoutSessionExerciseRepository workoutSessionExerciseRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final AnalysisRepository analysisRepository;

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminExerciseResponse> getExercises(
            int page,
            int size,
            String search,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            Boolean active,
            String sortBy,
            String sortDirection
    ) {
        Sort sort = resolveSort(sortBy, sortDirection);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)), sort);
        Specification<Exercise> spec = exerciseSpecification(search, category, difficulty, active);

        Page<Exercise> exercisePage = exerciseRepository.findAll(spec, pageable);

        Page<AdminExerciseResponse> mappedPage = exercisePage.map(this::mapToAdminResponse);
        return AdminPageResponse.from(mappedPage);
    }

    @Transactional(readOnly = true)
    public AdminExerciseResponse getExercise(Long id) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + id));
        return mapToAdminResponse(exercise);
    }

    @Transactional
    public AdminExerciseResponse createExercise(CreateAdminExerciseRequest request) {
        String trimmedName = request.name().trim();
        if (exerciseRepository.existsByNameIgnoreCase(trimmedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercise with name '" + trimmedName + "' already exists");
        }

        String code = (request.code() != null && !request.code().isBlank())
                ? request.code().trim().toUpperCase()
                : generateCodeFromName(trimmedName);

        if (exerciseRepository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exercise with code '" + code + "' already exists");
        }

        Exercise exercise = Exercise.builder()
                .name(trimmedName)
                .code(code)
                .category(request.category())
                .difficulty(request.difficulty())
                .description(request.description())
                .targetMuscles(request.targetMuscles())
                .instructions(request.instructions())
                .imageUrl(normalizeUrl(request.imageUrl()))
                .videoUrl(normalizeUrl(request.videoUrl()))
                .equipment(request.equipment())
                .active(request.active() != null ? request.active() : true)
                .build();

        Exercise saved = exerciseRepository.save(exercise);
        return mapToAdminResponse(saved);
    }

    @Transactional
    public AdminExerciseResponse updateExercise(Long id, UpdateAdminExerciseRequest request) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + id));

        String trimmedName = request.name().trim();
        if (exerciseRepository.existsByNameIgnoreCaseAndIdNot(trimmedName, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another exercise already has name '" + trimmedName + "'");
        }

        String code = (request.code() != null && !request.code().isBlank())
                ? request.code().trim().toUpperCase()
                : exercise.getCode();

        if (exerciseRepository.existsByCodeAndIdNot(code, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another exercise already has code '" + code + "'");
        }

        exercise.setName(trimmedName);
        exercise.setCode(code);
        exercise.setCategory(request.category());
        exercise.setDifficulty(request.difficulty());
        exercise.setDescription(request.description());
        exercise.setTargetMuscles(request.targetMuscles());
        exercise.setInstructions(request.instructions());
        exercise.setImageUrl(normalizeUrl(request.imageUrl()));
        exercise.setVideoUrl(normalizeUrl(request.videoUrl()));
        exercise.setEquipment(request.equipment());
        if (request.active() != null) {
            exercise.setActive(request.active());
        }

        Exercise updated = exerciseRepository.save(exercise);
        return mapToAdminResponse(updated);
    }

    @Transactional
    public AdminExerciseResponse updateStatus(Long id, boolean active) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + id));

        exercise.setActive(active);
        Exercise updated = exerciseRepository.save(exercise);
        return mapToAdminResponse(updated);
    }

    @Transactional
    public AdminExerciseDeleteResponse deleteExercise(Long id, boolean softDelete) {
        Exercise exercise = exerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + id));

        long planRefs = workoutPlanExerciseRepository.countByExerciseId(id);
        long sessionRefs = workoutSessionExerciseRepository.countByExerciseId(id);
        long prRefs = personalRecordRepository.countByExerciseId(id);
        long analysisRefs = analysisRepository.countByExerciseId(id);
        long totalRefs = planRefs + sessionRefs + prRefs + analysisRefs;

        if (totalRefs > 0) {
            if (softDelete) {
                exercise.setActive(false);
                exerciseRepository.save(exercise);
                return new AdminExerciseDeleteResponse(
                        true,
                        true,
                        "Exercise '" + exercise.getName() + "' is referenced in " + totalRefs + " training records and has been deactivated instead to preserve historical workout data."
                );
            }

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot permanently delete exercise '" + exercise.getName() + "' because it is referenced in " + totalRefs + " workout plans, sessions, or records. Deactivate it instead to preserve workout history."
            );
        }

        exerciseRepository.delete(exercise);
        return new AdminExerciseDeleteResponse(
                true,
                false,
                "Exercise '" + exercise.getName() + "' has been permanently deleted."
        );
    }

    private AdminExerciseResponse mapToAdminResponse(Exercise exercise) {
        long planRefs = workoutPlanExerciseRepository.countByExerciseId(exercise.getId());
        long sessionRefs = workoutSessionExerciseRepository.countByExerciseId(exercise.getId());
        long prRefs = personalRecordRepository.countByExerciseId(exercise.getId());
        long analysisRefs = analysisRepository.countByExerciseId(exercise.getId());
        boolean canDelete = (planRefs + sessionRefs + prRefs + analysisRefs) == 0;

        return new AdminExerciseResponse(
                exercise.getId(),
                exercise.getName(),
                exercise.getCode(),
                exercise.getCategory(),
                exercise.getDifficulty(),
                exercise.getDescription(),
                exercise.getTargetMuscles(),
                exercise.getInstructions(),
                exercise.getImageUrl(),
                exercise.getVideoUrl(),
                exercise.getEquipment(),
                exercise.isActive(),
                exercise.getCreatedAt(),
                planRefs,
                sessionRefs,
                canDelete
        );
    }

    private String generateCodeFromName(String name) {
        String clean = name.trim().toUpperCase()
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
        if (clean.isBlank()) {
            clean = "EXERCISE_" + System.currentTimeMillis();
        }
        if (clean.length() > 40) {
            clean = clean.substring(0, 40);
        }
        String candidate = clean;
        int counter = 1;
        while (exerciseRepository.existsByCode(candidate)) {
            candidate = clean + "_" + counter++;
        }
        return candidate;
    }

    private String normalizeUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        return url.trim();
    }

    private Sort resolveSort(String sortBy, String sortDirection) {
        String field = (sortBy != null && ALLOWED_SORT_FIELDS.contains(sortBy)) ? sortBy : "name";
        Sort.Direction direction = "DESC".equalsIgnoreCase(sortDirection) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field);
    }

    private Specification<Exercise> exerciseSpecification(
            String search,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            Boolean active
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate codeMatch = cb.like(cb.lower(root.get("code")), pattern);
                Predicate muscleMatch = cb.like(cb.lower(root.get("targetMuscles")), pattern);
                predicates.add(cb.or(nameMatch, codeMatch, muscleMatch));
            }

            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }

            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }

            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
