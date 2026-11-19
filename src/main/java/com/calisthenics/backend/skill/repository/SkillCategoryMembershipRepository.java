package com.calisthenics.backend.skill.repository;

import com.calisthenics.backend.skill.entity.SkillCategoryMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SkillCategoryMembershipRepository extends JpaRepository<SkillCategoryMembership, Long> {

    @Query("""
            SELECT m FROM SkillCategoryMembership m
            JOIN FETCH m.skill s
            WHERE s.active = true
            ORDER BY m.trainingType ASC, m.displayOrder ASC, s.displayOrder ASC, s.name ASC
            """)
    List<SkillCategoryMembership> findAllActiveWithSkillOrderByDisplay();

    List<SkillCategoryMembership> findBySkillId(Long skillId);
}
