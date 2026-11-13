package com.calisthenics.backend.gettingstarted.repository;

import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GettingStartedProgramRepository extends JpaRepository<GettingStartedProgram, Long> {

    @Query("SELECT p FROM GettingStartedProgram p WHERE p.published = true ORDER BY p.publishedAt DESC, p.updatedAt DESC LIMIT 1")
    Optional<GettingStartedProgram> findFirstPublished();

    @Query("SELECT p FROM GettingStartedProgram p ORDER BY p.updatedAt DESC LIMIT 1")
    Optional<GettingStartedProgram> findLatest();
}
