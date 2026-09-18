package com.xperience.hero.service;

import com.xperience.hero.dto.InviteeView;
import com.xperience.hero.dto.RsvpResponse;
import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.EventStatus;
import com.xperience.hero.entity.Invitee;
import com.xperience.hero.entity.RsvpStatus;
import com.xperience.hero.exception.ConflictException;
import com.xperience.hero.exception.NotFoundException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InviteeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Owns RSVP status, the Capacity Gate, and Waitlist Promotion (DESIGN.md -
 * Proposed Architecture: RSVP Engine). This is the highest-risk code in the
 * design (Concurrency Notes #1/#2/#3/#4) - every public method here locks
 * the Event row FIRST, inside one transaction, before reading or writing
 * anything capacity-related. That single lock is what makes the Capacity
 * Gate, Waitlist Promotion, and the Lock Check all serialize correctly
 * against each other and against Close/Cancel - there is deliberately no
 * separate row lock on the Invitee being promoted (see InviteeRepository).
 */
@Service
public class RsvpEngineService {

    private final EventRepository eventRepository;
    private final InviteeRepository inviteeRepository;

    public RsvpEngineService(EventRepository eventRepository, InviteeRepository inviteeRepository) {
        this.eventRepository = eventRepository;
        this.inviteeRepository = inviteeRepository;
    }

    @Transactional
    public InviteeView getView(String inviteeToken) {
        Invitee invitee = inviteeRepository.findByToken(inviteeToken)
                .orElseThrow(() -> new NotFoundException("Invalid link"));
        Event event = eventRepository.lockById(invitee.getEvent().getId())
                .orElseThrow(() -> new NotFoundException("Invalid link"));
        return InviteeView.of(invitee, isLocked(event));
    }

    @Transactional
    public InviteeView submitRsvp(String inviteeToken, RsvpResponse response) {
        Invitee invitee = inviteeRepository.findByToken(inviteeToken)
                .orElseThrow(() -> new NotFoundException("Invalid link"));

        // Lock Check + Capacity Gate must both evaluate against the SAME locked
        // Event row, in the SAME transaction as the write (Concurrency Note #3 - TOCTOU).
        Event event = eventRepository.lockById(invitee.getEvent().getId())
                .orElseThrow(() -> new NotFoundException("Invalid link"));

        assertNotLocked(event);

        RsvpStatus previousStatus = invitee.getStatus();
        RsvpStatus newStatus = resolveNewStatus(event, previousStatus, response);

        invitee.setStatus(newStatus);
        invitee.setRespondedAt(java.time.Instant.now());
        inviteeRepository.save(invitee);

        // A Confirmed slot was just freed -> promote the next waitlisted invitee,
        // in this same transaction, under the same Event lock (Concurrency Note #2).
        if (previousStatus == RsvpStatus.CONFIRMED && newStatus != RsvpStatus.CONFIRMED) {
            promoteNextWaitlisted(event);
        }

        return InviteeView.of(invitee, isLocked(event));
    }

    private RsvpStatus resolveNewStatus(Event event, RsvpStatus previousStatus, RsvpResponse response) {
        return switch (response) {
            case NO -> RsvpStatus.DECLINED;
            case MAYBE -> RsvpStatus.MAYBE; // Assumption #2: Maybe never touches capacity.
            case YES -> resolveYes(event, previousStatus);
        };
    }

    private RsvpStatus resolveYes(Event event, RsvpStatus previousStatus) {
        if (previousStatus == RsvpStatus.CONFIRMED) {
            return RsvpStatus.CONFIRMED; // idempotent re-submit, no capacity re-check needed.
        }
        Integer capacity = event.getMaxCapacity();
        if (capacity == null) {
            return RsvpStatus.CONFIRMED;
        }
        long confirmedCount = inviteeRepository.countByEventAndStatus(event, RsvpStatus.CONFIRMED);
        return confirmedCount < capacity ? RsvpStatus.CONFIRMED : RsvpStatus.WAITLISTED;
    }

    private void promoteNextWaitlisted(Event event) {
        inviteeRepository.findFirstByEventAndStatusOrderByRespondedAtAsc(event, RsvpStatus.WAITLISTED)
                .ifPresent(nextInLine -> {
                    nextInLine.setStatus(RsvpStatus.CONFIRMED);
                    inviteeRepository.save(nextInLine);
                });
    }

    private void assertNotLocked(Event event) {
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("This event has been cancelled");
        }
        if (event.getStatus() == EventStatus.CLOSED) {
            throw new ConflictException("This event is closed to new responses");
        }
        // Database's own clock, not app time (Data Ownership - Event: clock-disagreement risk).
        LocalDateTime dbNow = eventRepository.currentDbTime();
        if (!event.getStartTime().isAfter(dbNow)) {
            throw new ConflictException("This event has already started; RSVPs are locked");
        }
    }

    private boolean isLocked(Event event) {
        if (event.getStatus() != EventStatus.OPEN) {
            return true;
        }
        return !event.getStartTime().isAfter(eventRepository.currentDbTime());
    }
}
