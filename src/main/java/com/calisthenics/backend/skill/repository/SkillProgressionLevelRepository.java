package com.calisthenics.backend.skill.repository;

import com.calisthenics.backend.skill.entity.SkillProgressionLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillProgressionLevelRepository extends JpaRepository<SkillProgressionLevel, Long> {

    List<SkillProgressionLevel> findAllByOrderBySkillIdAscLevelNumberAsc();

    List<SkillProgressionLevel> findBySkillIdOrderByLevelNumberAsc(Long skillId);
}
