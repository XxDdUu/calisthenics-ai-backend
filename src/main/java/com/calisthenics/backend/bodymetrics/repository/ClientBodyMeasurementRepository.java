package com.calisthenics.backend.bodymetrics.repository;

import com.calisthenics.backend.bodymetrics.entity.ClientBodyMeasurement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ClientBodyMeasurementRepository extends JpaRepository<ClientBodyMeasurement, Long> {

    Optional<ClientBodyMeasurement> findFirstByClientIdOrderByMeasuredAtDesc(Long clientId);

    Page<ClientBodyMeasurement> findByClientId(Long clientId, Pageable pageable);

    @Query("SELECT m FROM ClientBodyMeasurement m WHERE m.client.id = :clientId " +
            "AND (:startDate IS NULL OR m.measuredAt >= :startDate) " +
            "AND (:endDate IS NULL OR m.measuredAt <= :endDate)")
    Page<ClientBodyMeasurement> findByClientIdWithDateRange(
            @Param("clientId") Long clientId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate,
            Pageable pageable
    );

    @Query("SELECT m FROM ClientBodyMeasurement m WHERE m.client.id = :clientId " +
            "AND (:startDate IS NULL OR m.measuredAt >= :startDate) " +
            "AND (:endDate IS NULL OR m.measuredAt <= :endDate) " +
            "ORDER BY m.measuredAt ASC")
    List<ClientBodyMeasurement> findByClientIdWithDateRangeAsc(
            @Param("clientId") Long clientId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate
    );

    List<ClientBodyMeasurement> findByClientIdOrderByMeasuredAtAsc(Long clientId);

    long countByClientId(Long clientId);
}

