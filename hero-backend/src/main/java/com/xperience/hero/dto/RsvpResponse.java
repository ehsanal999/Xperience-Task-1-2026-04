package com.xperience.hero.dto;

/** What an invitee actually submits (Fact #2). Distinct from the stored
 *  RsvpStatus, which also distinguishes CONFIRMED vs WAITLISTED for a Yes. */
public enum RsvpResponse {
    YES,
    NO,
    MAYBE
}
