package com.calisthenics.backend.profile.repository;

import com.calisthenics.backend.profile.entity.CoachProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.Set;

public interface CoachProfileRepository extends JpaRepository<CoachProfile, Long> {

    Optional<CoachProfile> findByUserId(Long userId);

    @Query("SELECT cp.user.id FROM CoachProfile cp WHERE cp.user.id IN :userIds")
    Set<Long> findUserIdsWithProfiles(@Param("userIds") Collection<Long> userIds);

    Optional<CoachProfile> findByUserIdAndIsPublicTrue(Long userId);

    @Query("SELECT cp FROM CoachProfile cp JOIN FETCH cp.user u WHERE cp.isPublic = true AND u.id <> :excludeUserId ORDER BY cp.createdAt DESC")
    List<CoachProfile> findAllPublicCoachesExcludingUser(@Param("excludeUserId") Long excludeUserId);

    @Query("SELECT cp FROM CoachProfile cp JOIN FETCH cp.user WHERE cp.isPublic = true ORDER BY cp.createdAt DESC")
    List<CoachProfile> findAllPublicCoaches();
}
