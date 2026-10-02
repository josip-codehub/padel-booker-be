package org.padel.booker.fixed;

import java.math.BigDecimal;

/** A standing weekly booking as shown to the admin. */
public record FixedBookingView(
        long id,
        long clubId,
        String clubName,
        long courtId,
        String courtName,
        int weekday,          // 1=Mon .. 7=Sun
        String weekdayName,
        String slotTimeFmt,
        String slotEndFmt,
        int slotCount,
        BigDecimal price,
        boolean active
) {}
