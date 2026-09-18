package com.xperience.hero.dto;

import com.xperience.hero.entity.Invitee;

public record InviteeView(
        String status,
        boolean locked,
        EventPublicSummary event
) {
    public static InviteeView of(Invitee invitee, boolean locked) {
        return new InviteeView(invitee.getStatus().name(), locked, EventPublicSummary.from(invitee.getEvent()));
    }
}
