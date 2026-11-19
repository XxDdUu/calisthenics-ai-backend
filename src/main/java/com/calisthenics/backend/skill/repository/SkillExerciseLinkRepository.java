package com.calisthenics.backend.skill.repository;

import com.calisthenics.backend.skill.entity.SkillExerciseLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SkillExerciseLinkRepository extends JpaRepository<SkillExerciseLink, Long> {

    @Query("""
            SELECT l FROM SkillExerciseLink l
            JOIN FETCH l.skill s
            LEFT JOIN FETCH l.progressionLevel p
            JOIN FETCH l.exercise e
            WHERE s.active = true
            ORDER BY s.id ASC, l.displayOrder ASC, e.id ASC
            """)
    List<SkillExerciseLink> findAllActiveWithDetails();

    @Query("""
            SELECT l FROM SkillExerciseLink l
            JOIN FETCH l.skill s
            LEFT JOIN FETCH l.progressionLevel p
            JOIN FETCH l.exercise e
            WHERE s.id = :skillId
            ORDER BY l.displayOrder ASC, e.id ASC
            """)
    List<SkillExerciseLink> findBySkillIdWithDetails(@Param("skillId") Long skillId);

    @Query("""
            SELECT l FROM SkillExerciseLink l
            JOIN FETCH l.skill s
            LEFT JOIN FETCH l.progressionLevel p
            JOIN FETCH l.exercise e
            WHERE e.id = :exerciseId AND s.active = true
            ORDER BY s.displayOrder ASC, l.displayOrder ASC
            """)
    List<SkillExerciseLink> findByExerciseIdWithDetails(@Param("exerciseId") Long exerciseId);
}
