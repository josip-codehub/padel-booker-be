package org.padel.booker.availability;

import java.math.BigDecimal;
import java.util.List;

/** DTOs returned to the Angular frontend for the availability grid. */
public final class AvailabilityViews {

    private AvailabilityViews() {}

    public record Slot(
            int time,          // decimal-hours x100, e.g. 700, 1450
            String timeFmt,    // "07:00"
            boolean booked,
            boolean mine,      // booked by the logged-in player
            boolean queued,    // a future slot already scheduled in our Queue (PENDING)
            BigDecimal price   // null when booked
    ) {}

    public record CourtDay(String date, List<Slot> slots) {}

    public record CourtWeek(
            long courtId,
            String courtName,
            String type,
            int reservationDuration,
            List<CourtDay> days
    ) {}

    public record Week(String from, String to, List<CourtWeek> courts) {}
}
