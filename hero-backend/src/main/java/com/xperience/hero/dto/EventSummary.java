package com.xperience.hero.dto;

import com.xperience.hero.entity.Event;

import java.time.LocalDateTime;

public record EventSummary(
        String title,
        String description,
        LocalDateTime startTime,
        String location,
        Integer maxCapacity,
        String status,
        String hostToken
) {
    public static EventSummary from(Event event) {
        return new EventSummary(
                event.getTitle(),
                event.getDescription(),
                event.getStartTime(),
                event.getLocation(),
                event.getMaxCapacity(),
                event.getStatus().name(),
                event.getHostToken()
        );
    }
}
