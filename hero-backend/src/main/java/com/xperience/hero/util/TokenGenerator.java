package com.xperience.hero.util;

import java.util.UUID;

/**
 * Resolution to the token-generation gap flagged during design review:
 * cryptographically random (UUID v4, backed by SecureRandom), never a
 * sequential id - this is what the "unguessable link token" invariant means.
 */
public final class TokenGenerator {

    private TokenGenerator() {
    }

    public static String newToken() {
        return UUID.randomUUID().toString();
    }
}
