package org.padel.booker.availability;

import org.padel.booker.client.SportyApiClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Builds the week availability grid from the upstream's server-precomputed {@code parsed_reservations}
 * (one live call, all courts, whole week). Nothing is cached — every request re-fetches.
 *
 * Slot time codes are decimal-hours x100: 700=07:00, 850=08:30, 1450=14:30, 2050=20:30, 2200=22:00.
 * A slot with {@code id == 0} is free (bookable) and carries a {@code price}; anything else is booked.
 */
@Service
public class AvailabilityService {

    private final SportyApiClient api;

    public AvailabilityService(SportyApiClient api) {
        this.api = api;
    }

    public AvailabilityViews.Week getWeek(LocalDate from, LocalDate to) {
        JsonNode resp = api.getCourtsWithAvailability(from, to);
        JsonNode courts = resp.has("data") ? resp.get("data") : resp;

        List<AvailabilityViews.CourtWeek> courtWeeks = new ArrayList<>();
        for (JsonNode court : courts) {
            long courtId = court.path("id").asLong();
            String name = court.path("name").asText();
            String type = court.path("type").asText();
            int duration = court.path("reservation_duration").asInt(90);
            JsonNode parsed = court.path("parsed_reservations");

            List<AvailabilityViews.CourtDay> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                days.add(new AvailabilityViews.CourtDay(d.toString(), slotsForDay(parsed.path(d.toString()))));
            }
            courtWeeks.add(new AvailabilityViews.CourtWeek(courtId, name, type, duration, days));
        }
        return new AvailabilityViews.Week(from.toString(), to.toString(), courtWeeks);
    }

    private List<AvailabilityViews.Slot> slotsForDay(JsonNode dayNode) {
        List<AvailabilityViews.Slot> slots = new ArrayList<>();
        if (dayNode == null || !dayNode.isObject()) return slots;

        // Sort by numeric time code.
        TreeMap<Integer, JsonNode> byTime = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : dayNode.properties()) {
            JsonNode slot = e.getValue();
            int time = slot.path("time").asInt(parseIntOr(e.getKey(), -1));
            if (time < 0) continue;
            if (slot.path("hidden").asBoolean(false)) continue; // blocked slot
            byTime.put(time, slot);
        }

        for (Map.Entry<Integer, JsonNode> e : byTime.entrySet()) {
            int time = e.getKey();
            JsonNode slot = e.getValue();
            boolean booked = slot.path("id").asLong(0) != 0;
            BigDecimal price = (!booked && slot.hasNonNull("price")) ? slot.get("price").decimalValue() : null;
            slots.add(new AvailabilityViews.Slot(time, fmt(time), booked, price));
        }
        return slots;
    }

    /** Decimal-hours x100 -> "HH:mm". 1450 -> "14:30", 700 -> "07:00". */
    private static String fmt(int timeCode) {
        int hours = timeCode / 100;
        int minutes = Math.round((timeCode % 100) * 0.6f); // .50 -> 30, .00 -> 0
        return String.format("%02d:%02d", hours, minutes);
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
