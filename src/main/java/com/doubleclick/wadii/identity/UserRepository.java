package com.doubleclick.wadii.identity;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    /** Serializes per-user quota checks (active listings). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> lockById(@Param("id") Long id);

    @Query("""
            select u from User u
            where (:q is null or lower(u.fullName) like :q or lower(u.email) like :q or u.phone like :q)
              and (:status is null or u.status = :status)
              and (:admin is null or u.admin = :admin)
            """)
    Page<User> search(@Param("q") String q, @Param("status") AccountStatus status, @Param("admin") Boolean admin, Pageable pageable);

    long countByCreatedAtBetween(Instant from, Instant to);
}
