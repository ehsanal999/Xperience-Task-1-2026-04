package com.xperience.hero.repository;

import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.Invitee;
import com.xperience.hero.entity.RsvpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InviteeRepository extends JpaRepository<Invitee, Long> {

    Optional<Invitee> findByToken(String token);

    List<Invitee> findByEventOrderByCreatedAtAsc(Event event);

    long countByEventAndStatus(Event event, RsvpStatus status);

    /**
     * Waitlist Promotion pick (Concurrency Note #2, Assumption #3 - FIFO).
     * Ordered by respondedAt, NOT createdAt: createdAt is invite time and never
     * changes, so ordering by it picks "invited first," not "joined the waitlist
     * first" - those differ whenever someone responds Maybe/changes their mind
     * after others have already gone Yes (bug found via manual testing).
     * respondedAt is updated on every RSVP submission, so it correctly reflects
     * the moment this invitee last transitioned into WAITLISTED.
     * Safe without its own row lock here because the caller already holds the
     * Event's pessimistic write lock (see EventRepository#lockByHostToken/lockById)
     * for the whole transaction - no other write can be touching this event's
     * invitees concurrently.
     */
    Optional<Invitee> findFirstByEventAndStatusOrderByRespondedAtAsc(Event event, RsvpStatus status);
}
