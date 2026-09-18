package com.xperience.hero.service;

import com.xperience.hero.entity.Event;
import com.xperience.hero.entity.Invitee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * STUB implementation: no SMTP is configured anywhere in this project (see
 * application.yml), so this does not actually send email - it logs what
 * would have been sent. Wiring a real mail sender (e.g. spring-boot-starter-mail
 * + SMTP credentials) is a follow-up, not something this class silently fakes.
 */
@Service
public class ConsoleEmailInviteDeliveryService implements InviteDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleEmailInviteDeliveryService.class);

    @Override
    public void sendInvite(Invitee invitee, Event event) {
        log.info("[Invite Delivery STUB] Would email {} for event '{}': RSVP link token = {}",
                invitee.getEmail(), event.getTitle(), invitee.getToken());
    }
}
