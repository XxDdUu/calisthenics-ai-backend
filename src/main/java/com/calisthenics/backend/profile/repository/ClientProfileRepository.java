package com.calisthenics.backend.profile.repository;

import com.calisthenics.backend.profile.entity.ClientProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClientProfileRepository extends JpaRepository<ClientProfile, Long> {

    Optional<ClientProfile> findByUserId(Long userId);

    @Query("SELECT cp.user.id FROM ClientProfile cp WHERE cp.user.id IN :userIds")
    Set<Long> findUserIdsWithProfiles(@Param("userIds") Collection<Long> userIds);
}
