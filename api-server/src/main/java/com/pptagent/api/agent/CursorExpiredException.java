package com.pptagent.api.agent;

/** Signals that an SSE cursor cannot be replayed in one bounded response. */
public class CursorExpiredException extends RuntimeException {
    public CursorExpiredException(String message) {
        super(message);
    }
}
