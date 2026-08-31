package org.padel.booker.availability;

import java.math.BigDecimal;
import java.util.List;

/** DTOs returned to the Angular frontend for the availability grid. */
public final class AvailabilityViews {

    private AvailabilityViews() {}

    public record Slot(
            int time,          // HHMM, e.g. 700, 1430
            String timeFmt,    // "07:00"
            boolean booked,
            BigDecimal price   // best-effort (min matching rule); authoritative price fetched at queue time
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
