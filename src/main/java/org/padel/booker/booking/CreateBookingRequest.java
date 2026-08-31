package org.padel.booker.booking;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateBookingRequest(
        @NotNull Long courtId,
        String courtName,
        @NotNull LocalDate playDate,
        @NotNull Integer slotTime   // HHMM, e.g. 700, 1430
) {}
