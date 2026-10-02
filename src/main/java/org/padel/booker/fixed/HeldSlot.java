package org.padel.booker.fixed;

import java.time.LocalDate;

/** One court/date/slot held by a standing weekly booking, for the availability overlay. */
public record HeldSlot(long courtId, LocalDate date, int code, Long userId, String username) {}
