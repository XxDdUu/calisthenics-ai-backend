package com.calisthenics.backend.skill.controller;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.skill.dto.SkillCatalogResponse;
import com.calisthenics.backend.skill.dto.SkillCategoryDto;
import com.calisthenics.backend.skill.dto.SkillDetailResponse;
import com.calisthenics.backend.skill.dto.SkillExerciseDto;
import com.calisthenics.backend.skill.dto.SkillReferenceDto;
import com.calisthenics.backend.skill.service.SkillCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillCatalogController {

    private final SkillCatalogService skillCatalogService;

    @GetMapping("/catalog")
    public ResponseEntity<SkillCatalogResponse> getCatalog(
            @RequestParam(required = false) ExerciseTrainingType trainingType,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) DifficultyLevel difficulty
    ) {
        return ResponseEntity.ok(skillCatalogService.getCatalog(trainingType, category, search, difficulty));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<SkillCategoryDto>> getCategories(
            @RequestParam(required = false) ExerciseTrainingType trainingType
    ) {
        return ResponseEntity.ok(skillCatalogService.getCategories(trainingType));
    }

    @GetMapping("/{codeOrId}")
    public ResponseEntity<SkillDetailResponse> getSkillDetail(
            @PathVariable String codeOrId,
            @RequestParam(required = false) DifficultyLevel difficulty,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(skillCatalogService.getSkillDetail(codeOrId, difficulty, search));
    }

    @GetMapping("/{codeOrId}/progressions")
    public ResponseEntity<List<SkillExerciseDto>> getSkillProgressionExercises(
            @PathVariable String codeOrId,
            @RequestParam(required = false) DifficultyLevel difficulty,
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(skillCatalogService.getSkillDetail(codeOrId, difficulty, search).getRelatedExercises());
    }

    @GetMapping("/by-exercise/{exerciseId}")
    public ResponseEntity<List<SkillReferenceDto>> getSkillsForExercise(@PathVariable Long exerciseId) {
        return ResponseEntity.ok(skillCatalogService.getSkillsByExerciseId(exerciseId));
    }
}
