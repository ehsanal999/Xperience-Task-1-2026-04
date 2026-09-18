package com.xperience.hero.controller;

import com.xperience.hero.dto.CreateEventRequest;
import com.xperience.hero.dto.EventDashboardResponse;
import com.xperience.hero.dto.EventSummary;
import com.xperience.hero.dto.InviteRequest;
import com.xperience.hero.dto.InviteeSummary;
import com.xperience.hero.entity.Event;
import com.xperience.hero.service.EventService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Host-side endpoints. The path segment {hostToken} IS the authorization
 * check (Q4) - there is no separate account/session, so every method here
 * resolves the Event exclusively through that token (Trust Boundaries -
 * Authorization Enforcement).
 */
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventService eventService;

    public EventController(EventService eventService) {
        this.eventService = eventService;
    }

    @PostMapping
    public EventSummary create(@RequestBody CreateEventRequest request) {
        Event event = eventService.createEvent(request);
        return EventSummary.from(event);
    }

    @GetMapping("/{hostToken}")
    public EventDashboardResponse dashboard(@PathVariable String hostToken) {
        return eventService.getDashboard(hostToken);
    }

    @PostMapping("/{hostToken}/invitees")
    public List<InviteeSummary> invite(@PathVariable String hostToken, @RequestBody InviteRequest request) {
        return eventService.invitePeople(hostToken, request.emails()).stream()
                .map(InviteeSummary::from)
                .toList();
    }

    @PostMapping("/{hostToken}/close")
    public EventDashboardResponse close(@PathVariable String hostToken) {
        eventService.closeEvent(hostToken);
        return eventService.getDashboard(hostToken);
    }

    @PostMapping("/{hostToken}/cancel")
    public EventDashboardResponse cancel(@PathVariable String hostToken) {
        eventService.cancelEvent(hostToken);
        return eventService.getDashboard(hostToken);
    }
}
