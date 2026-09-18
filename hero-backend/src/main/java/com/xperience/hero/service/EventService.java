package com.xperience.hero.service;

import com.xperience.hero.dto.CreateEventRequest;
import com.xperience.hero.dto.EventDashboardResponse;
import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.EventStatus;
import com.xperience.hero.entity.Invitee;
import com.xperience.hero.entity.RsvpStatus;
import com.xperience.hero.exception.BadRequestException;
import com.xperience.hero.exception.ConflictException;
import com.xperience.hero.exception.NotFoundException;
import com.xperience.hero.repository.EventRepository;
import com.xperience.hero.repository.InviteeRepository;
import com.xperience.hero.util.TokenGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns Event lifecycle, the invitee roster, and the host dashboard read
 * (DESIGN.md - Proposed Architecture: Event Service). Every method here
 * takes a hostToken because that link IS the host's identity (Q4) - there
 * is no separate login/session to check against.
 */
@Service
public class EventService {

    private final EventRepository eventRepository;
    private final InviteeRepository inviteeRepository;
    private final InviteDeliveryService inviteDeliveryService;

    public EventService(EventRepository eventRepository, InviteeRepository inviteeRepository,
                         InviteDeliveryService inviteDeliveryService) {
        this.eventRepository = eventRepository;
        this.inviteeRepository = inviteeRepository;
        this.inviteDeliveryService = inviteDeliveryService;
    }

    @Transactional
    public Event createEvent(CreateEventRequest request) {
        // Doesn't make sense to invite people to an event that can't happen -
        // checked against the database's own clock (Q3's precedent: app-server
        // time is never trusted for time comparisons in this design).
        if (!request.startTime().isAfter(eventRepository.currentDbTime())) {
            throw new BadRequestException("Event start time must be in the future");
        }
        Event event = new Event();
        event.setTitle(request.title());
        event.setDescription(request.description());
        event.setStartTime(request.startTime());
        event.setLocation(request.location());
        event.setMaxCapacity(request.maxCapacity());
        event.setHostToken(TokenGenerator.newToken());
        event.setStatus(EventStatus.OPEN);
        return eventRepository.save(event);
    }

    public Event requireByHostToken(String hostToken) {
        return eventRepository.findByHostToken(hostToken)
                .orElseThrow(() -> new NotFoundException("Invalid link"));
    }

    @Transactional
    public List<Invitee> invitePeople(String hostToken, List<String> emails) {
        Event event = requireByHostToken(hostToken);
        if (event.getStatus() != EventStatus.OPEN) {
            throw new ConflictException("Cannot invite people to a " + event.getStatus().name().toLowerCase() + " event");
        }
        List<Invitee> created = new ArrayList<>();
        for (String email : emails) {
            if (email == null || email.isBlank()) {
                continue;
            }
            Invitee invitee = new Invitee();
            invitee.setEvent(event);
            invitee.setEmail(email.trim());
            invitee.setToken(TokenGenerator.newToken());
            invitee.setStatus(RsvpStatus.PENDING);
            inviteeRepository.save(invitee);
            // Failure mode (Risk #7): a delivery failure here is currently invisible
            // to the host - the stub only logs, it never throws or retries.
            inviteDeliveryService.sendInvite(invitee, event);
            created.add(invitee);
        }
        return created;
    }

    @Transactional
    public void closeEvent(String hostToken) {
        // Locks the same Event row Concurrency Note #4 requires, so this can't
        // race a concurrent Submit RSVP into inconsistent state.
        Event event = eventRepository.lockByHostToken(hostToken)
                .orElseThrow(() -> new NotFoundException("Invalid link"));
        // Terminal-state guard (found via manual testing): without this, closing
        // an already-CANCELLED event silently overwrote it back to CLOSED.
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Event is already cancelled");
        }
        if (event.getStatus() == EventStatus.CLOSED) {
            throw new ConflictException("Event is already closed");
        }
        event.setStatus(EventStatus.CLOSED);
        eventRepository.save(event);
    }

    @Transactional
    public void cancelEvent(String hostToken) {
        Event event = eventRepository.lockByHostToken(hostToken)
                .orElseThrow(() -> new NotFoundException("Invalid link"));
        // CANCELLED is a true terminal state; CLOSED -> CANCELLED is still a
        // meaningful transition (close responses, then call the whole thing off).
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new ConflictException("Event is already cancelled");
        }
        event.setStatus(EventStatus.CANCELLED);
        eventRepository.save(event);
    }

    public EventDashboardResponse getDashboard(String hostToken) {
        Event event = requireByHostToken(hostToken);
        List<Invitee> invitees = inviteeRepository.findByEventOrderByCreatedAtAsc(event);
        long confirmed = inviteeRepository.countByEventAndStatus(event, RsvpStatus.CONFIRMED);
        long waitlisted = inviteeRepository.countByEventAndStatus(event, RsvpStatus.WAITLISTED);
        return EventDashboardResponse.from(event, invitees, confirmed, waitlisted);
    }
}
