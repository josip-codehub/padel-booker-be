package org.padel.booker.mine;

import java.math.BigDecimal;
import java.util.List;

/** A slimmed view of one of the logged-in player's reservations, for the "My Reservations" page. */
public record MyReservationView(
        long id,
        long clubId,
        long courtId,
        String courtName,
        String date,       // local ISO date
        String fromTime,   // local "HH:mm"
        String toTime,     // local "HH:mm"
        List<String> players,
        BigDecimal totalPrice,
        boolean isPaid,
        boolean needsPayment,
        boolean createdByMe,
        boolean recurring  // part of a repeating series
) {}
