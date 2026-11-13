package com.calisthenics.backend.gettingstarted.repository;

import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgramUserState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GettingStartedProgramUserStateRepository extends JpaRepository<GettingStartedProgramUserState, Long> {

    Optional<GettingStartedProgramUserState> findByUserIdAndProgramIdAndProgramVersion(Long userId, Long programId, Integer programVersion);

    Optional<GettingStartedProgramUserState> findByUserIdAndProgramId(Long userId, Long programId);
}
