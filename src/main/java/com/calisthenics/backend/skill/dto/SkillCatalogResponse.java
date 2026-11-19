package com.calisthenics.backend.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillCatalogResponse {
    @Builder.Default
    private List<TrainingTypeSummaryDto> trainingTypes = new ArrayList<>();
    @Builder.Default
    private List<SkillCategoryDto> categories = new ArrayList<>();
    @Builder.Default
    private List<SkillSummaryDto> skills = new ArrayList<>();
    private int totalCategories;
    private int totalSkills;
    private int totalLinkedExercises;
}
