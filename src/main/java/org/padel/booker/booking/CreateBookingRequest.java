package org.padel.booker.booking;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Create/schedule a booking. A booking may span several consecutive slots (one entry per slot),
 * e.g. Modesto = one 90-min slot, Silax = 2..4 thirty-min slots. {@code repeatWeekly} (admin only)
 * turns it into a standing weekly {@link org.padel.booker.domain.FixedBooking}.
 */
public record CreateBookingRequest(
        @NotNull Long clubId,
        @NotNull Long courtId,
        String courtName,
        @NotNull LocalDate playDate,
        Integer slotMinutes,                 // court reservation_duration; null = club default
        @NotNull @NotEmpty List<Slot> slots, // consecutive slots, each a start code + its grid price
        Boolean repeatWeekly                 // admin only; null/false = one-off
) {
    public record Slot(@NotNull Integer time, BigDecimal price) {}

    public List<Integer> codes() {
        return slots.stream().map(Slot::time).toList();
    }

    public boolean isRepeatWeekly() {
        return Boolean.TRUE.equals(repeatWeekly);
    }
}
