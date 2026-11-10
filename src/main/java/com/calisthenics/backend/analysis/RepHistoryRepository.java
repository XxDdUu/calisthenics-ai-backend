package com.calisthenics.backend.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RepHistoryRepository extends JpaRepository<RepHistory, Long> {

    List<RepHistory> findBySessionIdOrderByRepNumberAsc(String sessionId);
}
