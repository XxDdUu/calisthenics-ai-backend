package com.calisthenics.backend.chat.presence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPresenceRepository extends JpaRepository<UserPresenceRecord, Long> {
    java.util.List<UserPresenceRecord> findAllByStatus(String status);
}
