package com.xperience.hero.controller;

import com.xperience.hero.dto.InviteeView;
import com.xperience.hero.dto.RsvpSubmission;
import com.xperience.hero.service.RsvpEngineService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Invitee-side endpoints - scoped strictly by {inviteeToken}, never a sequential id. */
@RestController
@RequestMapping("/api/rsvp")
public class RsvpController {

    private final RsvpEngineService rsvpEngineService;

    public RsvpController(RsvpEngineService rsvpEngineService) {
        this.rsvpEngineService = rsvpEngineService;
    }

    @GetMapping("/{inviteeToken}")
    public InviteeView view(@PathVariable String inviteeToken) {
        return rsvpEngineService.getView(inviteeToken);
    }

    @PostMapping("/{inviteeToken}")
    public InviteeView submit(@PathVariable String inviteeToken, @RequestBody RsvpSubmission submission) {
        return rsvpEngineService.submitRsvp(inviteeToken, submission.response());
    }
}
