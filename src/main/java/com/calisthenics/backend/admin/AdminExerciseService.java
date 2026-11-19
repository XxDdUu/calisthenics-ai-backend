package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminExerciseDeleteResponse;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.AdminExerciseStatsResponse;
import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.admin.dto.CreateAdminExerciseRequest;
import com.calisthenics.backend.admin.dto.UpdateAdminExerciseRequest;
import com.calisthenics.backend.admin.dto.ExerciseMediaItemRequest;
import com.calisthenics.backend.analysis.AnalysisRepository;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseMediaItem;
import com.calisthenics.backend.exercise.ExerciseMediaMapper;
import com.calisthenics.backend.exercise.ExerciseMediaType;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.workout.WorkoutSessionExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class AdminExerciseService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "category", "difficulty", "createdAt", "active");

    private final ExerciseRepository exerciseRepository;
    private final WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    private final WorkoutSessionExerciseRepository workoutSessionExerciseRepository;
    private final PersonalRecordRepository personalRecordRepository;
    private final AnalysisRepository analysisRepository;
    private final MuscleTaxonomyService muscleTaxonomyService;

    public AdminExerciseService(
            ExerciseRepository exerciseRepository,
            WorkoutPlanExerciseRepository workoutPlanExerciseRepository,
            WorkoutSessionExerciseRepository workoutSessionExerciseRepository,
            PersonalRecordRepository personalRecordRepository,
            AnalysisRepository analysisRepository
    ) {
        this(
                exerciseRepository,
                workoutPlanExerciseRepository,
                workoutSessionExerciseRepository,
                personalRecordRepository,
                analysisRepository,
                new MuscleTaxonomyService()
        );
    }

    @Autowired
    public AdminExerciseService(
            ExerciseRepository exerciseRepository,
            WorkoutPlanExerciseRepository workoutPlanExerciseRepository,
            WorkoutSessionExerciseRepository workoutSessionExerciseRepository,
            PersonalRecordRepository personalRecordRepository,
            AnalysisRepository analysisRepository,
            MuscleTaxonomyService muscleTaxonomyService
    ) {
        this.exerciseRepository = exerciseRepository;
        this.workoutPlanExerciseRepository = workoutPlanExerciseRepository;
        this.workoutSessionExerciseRepository = workoutSessionExerciseRepository;
        this.personalRecordRepository = personalRecordRepository;
        this.analysisRepository = analysisRepository;
        this.muscleTaxonomyService = muscleTaxonomyService != null ? muscleTaxonomyService : new MuscleTaxonomyService();
    }

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
        return getExercises(page, size, search, category, difficulty, active, null, null, sortBy, sortDirection);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminExerciseResponse> getExercises(
            int page,
            int size,
            String search,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            Boolean active,
            ExerciseStatus status,
            ExerciseOrigin origin,
            String sortBy,
            String sortDirection
    ) {
        return getExercises(page, size, search, category, difficulty, active, status, origin, null, sortBy, sortDirection);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminExerciseResponse> getExercises(
            int page,
            int size,
            String search,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            Boolean active,
            ExerciseStatus status,
            ExerciseOrigin origin,
            ExerciseTrainingType trainingType,
            String sortBy,
            String sortDirection
    ) {
        Sort sort = resolveSort(sortBy, sortDirection);
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(MAX_PAGE_SIZE, Math.max(1, size)), sort);
        Specification<Exercise> spec = exerciseSpecification(search, category, difficulty, active, status, origin, trainingType);

        Page<Exercise> exercisePage = exerciseRepository.findAll(spec, pageable);

        Page<AdminExerciseResponse> mappedPage = exercisePage.map(this::mapToAdminResponse);
        return AdminPageResponse.from(mappedPage);
    }

    @Transactional(readOnly = true)
    public AdminExerciseStatsResponse getExerciseStats() {
        long total = exerciseRepository.count();
        long published = exerciseRepository.countByStatus(ExerciseStatus.PUBLISHED);
        long draft = exerciseRepository.countByStatus(ExerciseStatus.DRAFT);
        long aiRecommended = exerciseRepository.countByOrigin(ExerciseOrigin.AI_RECOMMENDED);
        return new AdminExerciseStatsResponse(total, published, draft, aiRecommended);
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

        ExerciseStatus resolvedStatus = request.status() != null
                ? request.status()
                : (request.active() != null
                        ? (request.active() ? ExerciseStatus.PUBLISHED : ExerciseStatus.DRAFT)
                        : ExerciseStatus.PUBLISHED);
        boolean resolvedActive = resolvedStatus == ExerciseStatus.PUBLISHED;
        ExerciseOrigin resolvedOrigin = request.origin() != null ? request.origin() : ExerciseOrigin.MANUAL;

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
                .active(resolvedActive)
                .status(resolvedStatus)
                .origin(resolvedOrigin)
                .trainingType(request.trainingType() != null ? request.trainingType() : ExerciseTrainingType.CALISTHENICS)
                .secondaryMuscles(request.secondaryMuscles())
                .exerciseType(request.exerciseType())
                .recommendedSets(request.recommendedSets())
                .recommendedReps(request.recommendedReps())
                .recommendedRestSeconds(request.recommendedRestSeconds())
                .commonMistakes(request.commonMistakes())
                .safetyConsiderations(request.safetyConsiderations())
                .progressionNotes(request.progressionNotes())
                .aliases(request.aliases())
                .build();

        normalizeMediaItems(exercise, request.imageUrl(), request.videoUrl(), request.mediaItems(), true);
        if (request.primaryMuscles() != null || request.secondaryMuscleIds() != null) {
            muscleTaxonomyService.syncExerciseMuscleTargets(
                    exercise, request.primaryMuscles(), request.secondaryMuscleIds());
        }
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

        String previousTargetMuscles = exercise.getTargetMuscles();
        String previousSecondaryMuscles = exercise.getSecondaryMuscles();

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
        normalizeMediaItems(
                exercise,
                request.imageUrl(),
                request.videoUrl(),
                request.mediaItems(),
                Boolean.TRUE.equals(request.replaceMediaItems())
        );
        if (request.status() != null) {
            exercise.setStatus(request.status());
        } else if (request.active() != null) {
            exercise.setActive(request.active());
        }
        if (request.origin() != null) {
            exercise.setOrigin(request.origin());
        }
        if (request.trainingType() != null) {
            exercise.setTrainingType(request.trainingType());
        }
        if (request.secondaryMuscles() != null) exercise.setSecondaryMuscles(request.secondaryMuscles());
        if (request.exerciseType() != null) exercise.setExerciseType(request.exerciseType());
        if (request.recommendedSets() != null) exercise.setRecommendedSets(request.recommendedSets());
        if (request.recommendedReps() != null) exercise.setRecommendedReps(request.recommendedReps());
        if (request.recommendedRestSeconds() != null) exercise.setRecommendedRestSeconds(request.recommendedRestSeconds());
        if (request.commonMistakes() != null) exercise.setCommonMistakes(request.commonMistakes());
        if (request.safetyConsiderations() != null) exercise.setSafetyConsiderations(request.safetyConsiderations());
        if (request.progressionNotes() != null) exercise.setProgressionNotes(request.progressionNotes());
        if (request.aliases() != null) exercise.setAliases(request.aliases());

        if (request.primaryMuscles() != null || request.secondaryMuscleIds() != null) {
            muscleTaxonomyService.syncExerciseMuscleTargets(
                    exercise, request.primaryMuscles(), request.secondaryMuscleIds());
        } else if (!Objects.equals(previousTargetMuscles, exercise.getTargetMuscles())
                || !Objects.equals(previousSecondaryMuscles, exercise.getSecondaryMuscles())) {
            muscleTaxonomyService.resyncFromLegacyTextIfPersisted(exercise);
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
    public List<AdminExerciseResponse> bulkUpdateStatus(List<Long> exerciseIds, boolean active) {
        if (exerciseIds == null || exerciseIds.isEmpty()) {
            return List.of();
        }
        List<Exercise> exercises = exerciseRepository.findAllById(exerciseIds);
        if (exercises.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No exercises found for the provided IDs");
        }
        for (Exercise exercise : exercises) {
            exercise.setActive(active);
        }
        List<Exercise> saved = exerciseRepository.saveAll(exercises);
        return saved.stream().map(this::mapToAdminResponse).toList();
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
        MuscleTaxonomyService.ResolvedMuscleTargets resolvedMuscles = muscleTaxonomyService.resolveTargets(exercise);

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
                exercise.getStatus(),
                exercise.getOrigin(),
                exercise.getCreatedAt(),
                planRefs,
                sessionRefs,
                canDelete,
                ExerciseMediaMapper.toResponses(exercise),
                exercise.getTrainingType(),
                exercise.getSecondaryMuscles(),
                exercise.getExerciseType(),
                exercise.getRecommendedSets(),
                exercise.getRecommendedReps(),
                exercise.getRecommendedRestSeconds(),
                exercise.getCommonMistakes(),
                exercise.getSafetyConsiderations(),
                exercise.getProgressionNotes(),
                exercise.getAliases(),
                resolvedMuscles.primaryMuscleIds(),
                resolvedMuscles.primaryMuscleIds(),
                resolvedMuscles.secondaryMuscleIds(),
                resolvedMuscles.muscleTargets()
        );
    }

    private void normalizeMediaItems(
            Exercise exercise,
            String imageUrl,
            String videoUrl,
            List<ExerciseMediaItemRequest> requestedItems,
            boolean replaceExisting
    ) {
        List<ExerciseMediaItem> existingItems = replaceExisting
                ? List.of()
                : new ArrayList<>(exercise.getMediaItems());
        Map<String, ExerciseMediaItem> normalizedItems = new LinkedHashMap<>();

        for (ExerciseMediaItem item : existingItems) {
            addMediaItem(normalizedItems, item.getType(), item.getUrl(), item.getThumbnailUrl(), item.getDisplayOrder());
        }

        if (replaceExisting) {
            exercise.getMediaItems().clear();
        }

        int nextDisplayOrder = normalizedItems.values().stream()
                .mapToInt(ExerciseMediaItem::getDisplayOrder)
                .max()
                .orElse(-1) + 1;
        if (requestedItems != null) {
            for (ExerciseMediaItemRequest item : requestedItems) {
                if (item == null) {
                    continue;
                }
                String url = normalizeMediaUrl(item.url());
                if (url == null) {
                    continue;
                }
                if (item.type() == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Media type is required.");
                }
                int displayOrder = item.displayOrder() != null
                        ? item.displayOrder()
                        : nextDisplayOrder++;
                addMediaItem(normalizedItems, item.type(), url, item.thumbnailUrl(), displayOrder);
                nextDisplayOrder = Math.max(nextDisplayOrder, displayOrder + 1);
            }
        }

        addMediaItem(normalizedItems, ExerciseMediaType.IMAGE, imageUrl, null, nextDisplayOrder++);
        addMediaItem(normalizedItems, ExerciseMediaType.VIDEO, videoUrl, null, nextDisplayOrder);

        exercise.getMediaItems().clear();
        exercise.getMediaItems().addAll(normalizedItems.values());
        exercise.getMediaItems().forEach(item -> item.setExercise(exercise));
    }

    private void addMediaItem(
            Map<String, ExerciseMediaItem> items,
            ExerciseMediaType type,
            String url,
            String thumbnailUrl,
            int displayOrder
    ) {
        String normalizedUrl = normalizeMediaUrl(url);
        if (normalizedUrl == null) {
            return;
        }
        if (type != ExerciseMediaType.IMAGE && type != ExerciseMediaType.VIDEO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported media type.");
        }
        items.putIfAbsent(normalizedUrl, ExerciseMediaItem.builder()
                .type(type)
                .url(normalizedUrl)
                .thumbnailUrl(normalizeUrl(thumbnailUrl))
                .displayOrder(displayOrder)
                .build());
    }

    private String normalizeMediaUrl(String url) {
        String normalized = normalizeUrl(url);
        return normalized == null || normalized.isBlank() ? null : normalized;
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
            Boolean active,
            ExerciseStatus status,
            ExerciseOrigin origin,
            ExerciseTrainingType trainingType
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nameMatch = cb.like(cb.lower(root.get("name")), pattern);
                Predicate codeMatch = cb.like(cb.lower(root.get("code")), pattern);
                Predicate muscleMatch = cb.like(cb.lower(root.get("targetMuscles")), pattern);
                Predicate aliasMatch = cb.like(cb.lower(cb.coalesce(root.get("aliases"), "")), pattern);
                predicates.add(cb.or(nameMatch, codeMatch, muscleMatch, aliasMatch));
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

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (origin != null) {
                predicates.add(cb.equal(root.get("origin"), origin));
            }

            if (trainingType != null) {
                predicates.add(cb.equal(root.get("trainingType"), trainingType));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
