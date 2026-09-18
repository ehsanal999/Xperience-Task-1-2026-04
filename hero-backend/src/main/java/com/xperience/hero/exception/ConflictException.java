package com.xperience.hero.exception;

/** Thrown when a mutation is rejected by the Lock Check (closed/cancelled/past start). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
