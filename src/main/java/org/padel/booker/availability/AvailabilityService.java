package org.padel.booker.availability;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.config.SportyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

/**
 * Builds the week availability grid from live data: courts (+ working_hours) minus existing
 * reservations. Nothing is cached — every call fetches fresh from the upstream API.
 */
@Service
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final SportyApiClient api;
    private final ZoneId zone;

    public AvailabilityService(SportyApiClient api, SportyProperties props) {
        this.api = api;
        this.zone = ZoneId.of(props.getTimezone());
    }

    public AvailabilityViews.Week getWeek(LocalDate from, LocalDate to) {
        JsonNode courts = arrayOf(api.getCourts());
        Set<String> booked = bookedKeys(api.getReservations(from, to, null));

        List<AvailabilityViews.CourtWeek> courtWeeks = new ArrayList<>();
        for (JsonNode court : courts) {
            if (!court.has("working_hours")) continue;
            long courtId = court.path("id").asLong();
            String name = court.path("name").asText();
            String type = court.path("type").asText();
            int duration = court.path("reservation_duration").asInt(90);
            JsonNode workingHours = court.path("working_hours");

            List<AvailabilityViews.CourtDay> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                days.add(new AvailabilityViews.CourtDay(d.toString(),
                        slotsForDay(courtId, d, duration, workingHours, booked)));
            }
            courtWeeks.add(new AvailabilityViews.CourtWeek(courtId, name, type, duration, days));
        }
        return new AvailabilityViews.Week(from.toString(), to.toString(), courtWeeks);
    }

    private List<AvailabilityViews.Slot> slotsForDay(long courtId, LocalDate date, int duration,
                                                     JsonNode workingHours, Set<String> booked) {
        int dow = date.getDayOfWeek().getValue(); // 1=Mon .. 7=Sun
        // time-of-day minutes -> min price among matching active rules
        TreeMap<Integer, BigDecimal> slotPrices = new TreeMap<>();

        for (JsonNode wh : workingHours) {
            if (wh.path("working").asInt(1) != 1) continue;
            if (!worksOn(wh, dow)) continue;
            if (!activeOn(wh, date)) continue;

            int startMin = parseMinutes(wh.path("start_time").asText(null));
            int endMin = parseMinutes(wh.path("end_time").asText(null));
            if (startMin < 0 || endMin < 0) continue;
            if (endMin <= startMin) endMin += 1440; // window crosses midnight

            BigDecimal price = wh.hasNonNull("price") ? wh.get("price").decimalValue() : null;

            for (int s = startMin; s + duration <= endMin; s += duration) {
                if (s >= 1440) continue; // slot starts on the next calendar day
                slotPrices.merge(s, price, AvailabilityService::minPrice);
            }
        }

        List<AvailabilityViews.Slot> slots = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> e : slotPrices.entrySet()) {
            int min = e.getKey();
            int hhmm = toHhmm(min);
            String fmt = fmt(min);
            boolean isBooked = booked.contains(key(courtId, date, min));
            slots.add(new AvailabilityViews.Slot(hhmm, fmt, isBooked, e.getValue()));
        }
        return slots;
    }

    private Set<String> bookedKeys(JsonNode reservationsResponse) {
        Set<String> set = new HashSet<>();
        for (JsonNode r : arrayOf(reservationsResponse)) {
            JsonNode courtNode = r.path("court");
            long courtId = courtNode.path("id").asLong();
            String fromStr = r.path("from").asText(null);
            if (courtId == 0 || fromStr == null) continue;
            ZonedDateTime localStart = Instant.parse(fromStr).atZone(zone);
            int min = localStart.getHour() * 60 + localStart.getMinute();
            set.add(key(courtId, localStart.toLocalDate(), min));
        }
        return set;
    }

    // ---- helpers ----

    private boolean worksOn(JsonNode wh, int dow) {
        JsonNode days = wh.path("working_days");
        if (days.isArray()) {
            for (JsonNode d : days) {
                if (d.asInt(-1) == dow || d.asText("").equals(String.valueOf(dow))) return true;
            }
            return false;
        }
        return true;
    }

    private boolean activeOn(JsonNode wh, LocalDate date) {
        Instant noon = date.atTime(12, 0).atZone(zone).toInstant();
        Instant activeFrom = parseInstant(wh.path("active_from").asText(null));
        Instant activeTo = parseInstant(wh.path("active_to").asText(null));
        if (activeFrom != null && noon.isBefore(activeFrom)) return false;
        if (activeTo != null && noon.isAfter(activeTo)) return false;
        return true;
    }

    private static Instant parseInstant(String s) {
        if (s == null) return null;
        try {
            return OffsetDateTime.parse(s).toInstant();
        } catch (RuntimeException e) {
            try { return Instant.parse(s); } catch (RuntimeException e2) { return null; }
        }
    }

    private static BigDecimal minPrice(BigDecimal a, BigDecimal b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.compareTo(b) <= 0 ? a : b;
    }

    private static int parseMinutes(String hhmm) {
        if (hhmm == null || !hhmm.contains(":")) return -1;
        String[] p = hhmm.split(":");
        try {
            return Integer.parseInt(p[0].trim()) * 60 + Integer.parseInt(p[1].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static int toHhmm(int min) {
        return (min / 60) * 100 + (min % 60);
    }

    private static String fmt(int min) {
        return String.format("%02d:%02d", min / 60, min % 60);
    }

    private static String key(long courtId, LocalDate date, int minutes) {
        return courtId + "|" + date + "|" + minutes;
    }

    /** Unwrap {data:[...]} or accept a bare array. */
    private static JsonNode arrayOf(JsonNode node) {
        if (node == null) return JsonNodeFactory.instance.arrayNode();
        if (node.has("data")) return node.get("data");
        return node;
    }
}
