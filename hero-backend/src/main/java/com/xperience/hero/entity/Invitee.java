package com.xperience.hero.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Design reference: DESIGN.md - Data Ownership and State Model.
 * `status` is the single mutable field the Capacity Gate / Waitlist Promotion
 * operate on. `respondedAt` (not `createdAt`) is the FIFO waitlist order key
 * (Assumption #3) - it's updated on every response, so it reflects when an
 * invitee actually joined the waitlist, not when they were invited.
 * `version` backs the optimistic-concurrency control named in Concurrency Note #6;
 * the real cross-request race (Concurrency Notes #1/#2) is closed by the Event-row
 * lock taken in RsvpEngineService, not by this field.
 */
@Entity
@Table(name = "invitees")
@Getter
@Setter
public class Invitee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(nullable = false)
    private String email;

    /** The invitee's only credential (Non-Goal #6) - unguessable, never a sequential id. */
    @Column(nullable = false, unique = true)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RsvpStatus status = RsvpStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Version
    private Long version;
}
