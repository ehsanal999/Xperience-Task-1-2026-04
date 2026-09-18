package com.xperience.hero.dto;

import com.xperience.hero.entity.Invitee;

public record InviteeSummary(
        String email,
        String status,
        String token
) {
    public static InviteeSummary from(Invitee invitee) {
        return new InviteeSummary(invitee.getEmail(), invitee.getStatus().name(), invitee.getToken());
    }
}
