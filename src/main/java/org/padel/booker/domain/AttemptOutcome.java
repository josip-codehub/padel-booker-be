package org.padel.booker.domain;

public enum AttemptOutcome {
    /** Reservation created. */
    SUCCESS,
    /** Slot not bookable yet (before midnight) or already taken. */
    COURT_UNAVAILABLE,
    /** Token rejected (triggers a refresh + retry). */
    AUTH_FAILED,
    /** Any other error (network, 5xx, unexpected payload). */
    ERROR
}
