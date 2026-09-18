package com.xperience.hero.exception;

/** Thrown for invalid input on a request the client controls (e.g. a past startTime). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
