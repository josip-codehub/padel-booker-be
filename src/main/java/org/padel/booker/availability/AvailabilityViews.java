package org.padel.booker.availability;

import java.math.BigDecimal;
import java.util.List;

/** DTOs returned to the Angular frontend for the availability grid. */
public final class AvailabilityViews {

    private AvailabilityViews() {}

    public record SlotPlayer(String name, String image) {}

    public record Slot(
            int time,          // decimal-hours x100, e.g. 700, 1450
            String timeFmt,    // "07:00"
            boolean booked,
            boolean mine,      // booked by the logged-in player (on Sporty)
            boolean queued,    // a future slot already scheduled in our Queue (PENDING, any app user)
            boolean queuedByMe,// the queued slot is mine
            String queuedBy,   // app username who scheduled it (null if not queued)
            BigDecimal price,  // null when booked
            String bookedBy,   // display name of who created the reservation (null when free)
            List<SlotPlayer> players // participants on a booked slot (empty when free)
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
