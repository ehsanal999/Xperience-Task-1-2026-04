package com.xperience.hero.dto;

import java.time.LocalDateTime;

public record CreateEventRequest(
        String title,
        String description,
        LocalDateTime startTime,
        String location,
        Integer maxCapacity
) {
}
