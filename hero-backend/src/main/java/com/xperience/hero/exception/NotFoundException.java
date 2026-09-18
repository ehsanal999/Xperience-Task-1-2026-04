package com.xperience.hero.exception;

/** Thrown for an invalid/foreign token - mapped to a generic 404, never leaking why. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
