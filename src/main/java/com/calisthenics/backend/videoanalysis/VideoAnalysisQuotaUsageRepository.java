package com.calisthenics.backend.videoanalysis;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface VideoAnalysisQuotaUsageRepository extends JpaRepository<VideoAnalysisQuotaUsage, Long> {

    Optional<VideoAnalysisQuotaUsage> findByUserIdAndQuotaDate(Long userId, LocalDate quotaDate);

    @Query("SELECT q.usedCount FROM VideoAnalysisQuotaUsage q WHERE q.userId = :userId AND q.quotaDate = :quotaDate")
    Optional<Integer> findUsedCountByUserIdAndQuotaDate(@Param("userId") Long userId, @Param("quotaDate") LocalDate quotaDate);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            INSERT INTO video_analysis_quota_usages (user_id, quota_date, used_count, daily_limit, created_at, updated_at)
            VALUES (:userId, :quotaDate, 0, :dailyLimit, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") Long userId,
            @Param("quotaDate") LocalDate quotaDate,
            @Param("dailyLimit") int dailyLimit
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE video_analysis_quota_usages
            SET used_count = used_count + 1,
                daily_limit = :dailyLimit,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND quota_date = :quotaDate
              AND used_count < :dailyLimit
            """, nativeQuery = true)
    int incrementUsedCountIfUnderLimit(
            @Param("userId") Long userId,
            @Param("quotaDate") LocalDate quotaDate,
            @Param("dailyLimit") int dailyLimit
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE video_analysis_quota_usages
            SET used_count = used_count + 1,
                daily_limit = :dailyLimit,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND quota_date = :quotaDate
            """, nativeQuery = true)
    int incrementUsedCountUnconditional(
            @Param("userId") Long userId,
            @Param("quotaDate") LocalDate quotaDate,
            @Param("dailyLimit") int dailyLimit
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE video_analysis_quota_usages
            SET used_count = CASE WHEN used_count > 0 THEN used_count - 1 ELSE 0 END,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND quota_date = :quotaDate
            """, nativeQuery = true)
    int decrementUsedCount(
            @Param("userId") Long userId,
            @Param("quotaDate") LocalDate quotaDate
    );

    @Modifying(flushAutomatically = true)
    @Query(value = """
            UPDATE video_analysis_quota_usages
            SET daily_limit = :dailyLimit,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_id = :userId
              AND quota_date = :quotaDate
            """, nativeQuery = true)
    int updateDailyLimit(
            @Param("userId") Long userId,
            @Param("quotaDate") LocalDate quotaDate,
            @Param("dailyLimit") int dailyLimit
    );
}
