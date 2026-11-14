package com.calisthenics.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    List<User> findByRoleAndIdNotOrderByFullNameAsc(Role role, Long id);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByFirebaseUid(String firebaseUid);

    Optional<User> findByGoogleId(String googleId);

    boolean existsByEmail(String email);

    boolean existsByFirebaseUid(String firebaseUid);

    boolean existsByGoogleId(String googleId);

    long countByRole(Role role);

    long countByRoleAndCreatedAtGreaterThanEqual(Role role, ZonedDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = :role order by u.id")
    List<User> findByRoleForUpdate(@Param("role") Role role);

    @Query(value = "select * from users where id = :id for update", nativeQuery = true)
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query(value = "select * from users where lower(email) = lower(:email) for update", nativeQuery = true)
    Optional<User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    @Query(value = "select * from users order by id for update", nativeQuery = true)
    List<User> lockAllForAdminBootstrap();
}
