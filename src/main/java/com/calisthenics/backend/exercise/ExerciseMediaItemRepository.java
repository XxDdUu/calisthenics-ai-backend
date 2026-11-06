package com.calisthenics.backend.exercise;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExerciseMediaItemRepository extends JpaRepository<ExerciseMediaItem, Long> {
}
