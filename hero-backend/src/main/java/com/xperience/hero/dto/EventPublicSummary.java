package com.xperience.hero.dto;

import com.xperience.hero.entity.Event;

import java.time.LocalDateTime;

/**
 * Event fields safe to show an invitee. Deliberately excludes hostToken -
 * EventSummary is for the host's own dashboard only; embedding it in an
 * invitee-facing response would leak the host's management credential
 * across the Q4 trust boundary.
 */
public record EventPublicSummary(
        String title,
        String description,
        LocalDateTime startTime,
        String location,
        String status
) {
    public static EventPublicSummary from(Event event) {
        return new EventPublicSummary(
                event.getTitle(),
                event.getDescription(),
                event.getStartTime(),
                event.getLocation(),
                event.getStatus().name()
        );
    }
}
