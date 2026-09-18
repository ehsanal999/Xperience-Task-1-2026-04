package com.xperience.hero.dto;

import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.Invitee;

import java.util.List;

public record EventDashboardResponse(
        EventSummary event,
        long confirmedCount,
        long waitlistedCount,
        List<InviteeSummary> invitees
) {
    public static EventDashboardResponse from(Event event, List<Invitee> invitees, long confirmedCount, long waitlistedCount) {
        return new EventDashboardResponse(
                EventSummary.from(event),
                confirmedCount,
                waitlistedCount,
                invitees.stream().map(InviteeSummary::from).toList()
        );
    }
}
