package org.padel.booker.club;

/** A club the current user can use, with the rules the UI needs to drive booking. */
public record ClubView(
        long clubId,
        String name,
        int slotMinutes,   // default/hint; actual granularity is per-court in the availability grid
        int minSlots,
        int maxSlots,
        int maxSpanDays
) {}
