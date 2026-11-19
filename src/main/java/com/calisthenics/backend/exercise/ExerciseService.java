package com.calisthenics.backend.exercise;

import com.calisthenics.backend.exercise.dto.CreateExerciseRequest;
import com.calisthenics.backend.exercise.dto.ExerciseResponse;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.skill.dto.SkillReferenceDto;
import com.calisthenics.backend.skill.service.SkillCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExerciseService {

    private final ExerciseRepository exerciseRepository;
    private final MuscleTaxonomyService muscleTaxonomyService;
    private final SkillCatalogService skillCatalogService;

    public ExerciseService(ExerciseRepository exerciseRepository) {
        this(exerciseRepository, new MuscleTaxonomyService(), null);
    }

    public ExerciseService(ExerciseRepository exerciseRepository, MuscleTaxonomyService muscleTaxonomyService) {
        this(exerciseRepository, muscleTaxonomyService, null);
    }

    @Autowired
    public ExerciseService(
            ExerciseRepository exerciseRepository,
            MuscleTaxonomyService muscleTaxonomyService,
            SkillCatalogService skillCatalogService
    ) {
        this.exerciseRepository = exerciseRepository;
        this.muscleTaxonomyService = muscleTaxonomyService != null ? muscleTaxonomyService : new MuscleTaxonomyService();
        this.skillCatalogService = skillCatalogService;
    }

    @Transactional(readOnly = true)
    public List<ExerciseResponse> getAllExercises() {
        List<Exercise> published = exerciseRepository.findAll().stream()
                .filter(this::isPublished)
                .toList();
        Map<Long, List<SkillReferenceDto>> refsByExerciseId = skillCatalogService != null
                ? skillCatalogService.getSkillReferencesByExerciseIds(published.stream().map(Exercise::getId).toList())
                : Map.of();
        return published.stream()
                .map(ex -> mapToResponse(ex, refsByExerciseId.getOrDefault(ex.getId(), List.of())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ExerciseResponse getExerciseById(Long id) {
        Exercise exercise = exerciseRepository.findById(id)
                .filter(this::isPublished)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with id: " + id));
        return mapToResponse(exercise);
    }

    @Transactional(readOnly = true)
    public ExerciseResponse getExerciseByCode(String code) {
        Exercise exercise = exerciseRepository.findByCode(code)
                .filter(this::isPublished)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Exercise not found with code: " + code));
        return mapToResponse(exercise);
    }

    private boolean isPublished(Exercise exercise) {
        return exercise != null && exercise.isActive() && exercise.getStatus() == ExerciseStatus.PUBLISHED;
    }

    @Transactional
    public ExerciseResponse createExercise(CreateExerciseRequest request) {
        if (exerciseRepository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Exercise with code " + request.getCode() + " already exists");
        }

        Exercise exercise = Exercise.builder()
                .name(request.getName())
                .code(request.getCode().toUpperCase())
                .category(request.getCategory())
                .difficulty(request.getDifficulty())
                .description(request.getDescription())
                .targetMuscles(request.getTargetMuscles())
                .instructions(request.getInstructions())
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build();

        if (request.getPrimaryMuscles() != null || request.getSecondaryMuscleIds() != null) {
            muscleTaxonomyService.syncExerciseMuscleTargets(
                    exercise, request.getPrimaryMuscles(), request.getSecondaryMuscleIds());
        }

        Exercise saved = exerciseRepository.save(exercise);
        return mapToResponse(saved);
    }

    public ExerciseResponse mapToResponse(Exercise exercise) {
        List<SkillReferenceDto> refs = skillCatalogService != null && exercise.getId() != null
                ? skillCatalogService.getSkillsByExerciseId(exercise.getId())
                : List.of();
        return mapToResponse(exercise, refs);
    }

    private ExerciseResponse mapToResponse(Exercise exercise, List<SkillReferenceDto> refs) {
        MuscleTaxonomyService.ResolvedMuscleTargets resolvedMuscles = muscleTaxonomyService.resolveTargets(exercise);
        List<String> skillCodes = refs.stream()
                .map(SkillReferenceDto::getSkillCode)
                .distinct()
                .toList();
        List<String> skillNames = refs.stream()
                .map(SkillReferenceDto::getSkillName)
                .distinct()
                .toList();
        SkillReferenceDto primaryRef = refs.isEmpty() ? null : refs.get(0);
        return ExerciseResponse.builder()
                .id(exercise.getId())
                .name(exercise.getName())
                .code(exercise.getCode())
                .category(exercise.getCategory())
                .difficulty(exercise.getDifficulty())
                .description(exercise.getDescription())
                .targetMuscles(exercise.getTargetMuscles())
                .instructions(exercise.getInstructions())
                .imageUrl(exercise.getImageUrl())
                .videoUrl(exercise.getVideoUrl())
                .equipment(exercise.getEquipment())
                .active(exercise.isActive())
                .status(exercise.getStatus())
                .origin(exercise.getOrigin())
                .mediaItems(ExerciseMediaMapper.toResponses(exercise))
                .trainingType(exercise.getTrainingType())
                .secondaryMuscles(exercise.getSecondaryMuscles())
                .exerciseType(exercise.getExerciseType())
                .recommendedSets(exercise.getRecommendedSets())
                .recommendedReps(exercise.getRecommendedReps())
                .recommendedRestSeconds(exercise.getRecommendedRestSeconds())
                .commonMistakes(exercise.getCommonMistakes())
                .safetyConsiderations(exercise.getSafetyConsiderations())
                .progressionNotes(exercise.getProgressionNotes())
                .primaryMuscles(resolvedMuscles.primaryMuscleIds())
                .primaryMuscleIds(resolvedMuscles.primaryMuscleIds())
                .secondaryMuscleIds(resolvedMuscles.secondaryMuscleIds())
                .muscleTargets(resolvedMuscles.muscleTargets())
                .subcategory(primaryRef != null ? primaryRef.getSubcategory() : null)
                .skillCodes(skillCodes)
                .skillNames(skillNames)
                .progressionLevel(primaryRef != null ? primaryRef.getProgressionLevelTitle() : null)
                .videoAnalysisSupported(SkillCatalogService.resolveAnalysisExerciseCode(exercise) != null)
                .build();
    }
}
