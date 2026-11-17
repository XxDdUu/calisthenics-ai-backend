package com.calisthenics.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipEntitlementRepository extends JpaRepository<MembershipEntitlement, Long> {

    Optional<MembershipEntitlement> findByUserIdAndGrantSource(Long userId, EntitlementGrantSource grantSource);

    boolean existsByUserIdAndGrantSourceAndStatus(
            Long userId,
            EntitlementGrantSource grantSource,
            EntitlementStatus status
    );

    List<MembershipEntitlement> findByUserIdInAndGrantSource(
            Collection<Long> userIds,
            EntitlementGrantSource grantSource
    );
}
