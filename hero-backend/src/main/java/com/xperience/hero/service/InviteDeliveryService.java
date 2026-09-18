package com.xperience.hero.service;

import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.Invitee;

/**
 * Design reference: DESIGN.md - Proposed Architecture (Invite Delivery), Q1.
 * Kept behind an interface so a future channel change doesn't leak into
 * EventService or RsvpEngineService.
 */
public interface InviteDeliveryService {
    void sendInvite(Invitee invitee, Event event);
}
