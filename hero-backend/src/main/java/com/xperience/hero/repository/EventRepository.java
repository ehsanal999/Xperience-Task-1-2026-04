package com.xperience.hero.repository;

import com.xperience.hero.entity.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    Optional<Event> findByHostToken(String hostToken);

    /**
     * Locking read backing Concurrency Notes #1/#2/#4: every write that touches
     * capacity, waitlist promotion, or event status (close/cancel) takes this
     * lock first, serializing all of it per-Event for the rest of the transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.hostToken = :hostToken")
    Optional<Event> lockByHostToken(@Param("hostToken") String hostToken);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> lockById(@Param("id") Long id);

    /**
     * The database's own clock, per the Lock Check control in Concurrency Note #3 -
     * app-server time is never trusted for the event-start comparison. Uses
     * LOCALTIMESTAMP (not CURRENT_TIMESTAMP) so Postgres returns a
     * timestamp-without-time-zone that Hibernate maps to LocalDateTime, matching
     * Event.startTime's type - CURRENT_TIMESTAMP is timestamptz and maps to
     * Instant instead, which throws a ClassCastException here.
     */
    @Query(value = "select localtimestamp", nativeQuery = true)
    LocalDateTime currentDbTime();
}
