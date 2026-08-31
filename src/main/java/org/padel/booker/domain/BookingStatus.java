package org.padel.booker.domain;

public enum BookingStatus {
    /** Waiting for its target fire time. */
    PENDING,
    /** The midnight burst is currently running. */
    RUNNING,
    /** A reservation was created successfully. */
    SUCCEEDED,
    /** The burst finished without securing the slot. */
    FAILED,
    /** Cancelled by the user before firing. */
    CANCELLED
}
