package com.xperience.hero.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Design reference: DESIGN.md - Invariants, Data Ownership and State Model.
 * Locked state is intentionally NOT a column here (Q3): every mutation must
 * evaluate status + startTime against the database's own clock at write time,
 * never a precomputed flag.
 */
@Entity
@Table(name = "events")
@Getter
@Setter
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private String location;

    @Column(name = "max_capacity")
    private Integer maxCapacity;

    /** The host's only credential (Q4) - bearer link, never a sequential id. */
    @Column(name = "host_token", nullable = false, unique = true)
    private String hostToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status = EventStatus.OPEN;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
