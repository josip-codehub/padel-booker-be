package org.padel.booker.booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.QueuedBooking;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Builds the POST /reservation body, mirroring exactly what the Sporty Plus web app sends. The
 * {@code times[]} array carries one object per slot (consecutive 30/90-min slots). The top-level
 * "players" array is [ <yourTeam>, {} ] for a solo booking.
 */
@Component
public class ReservationPayloadBuilder {

    private final ObjectMapper mapper;
    private final SportyProperties props;

    public ReservationPayloadBuilder(ObjectMapper mapper, SportyProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /** The players array to persist with a booking: [team, {}]. */
    public ArrayNode playersArray(JsonNode team) {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(team.deepCopy());
        arr.add(mapper.createObjectNode()); // second slot, empty, as the web app sends
        return arr;
    }

    /** Build the {@code times[]} array from the requested slots (start code + its grid price). */
    public ArrayNode buildTimes(List<CreateBookingRequest.Slot> slots) {
        ArrayNode times = mapper.createArrayNode();
        for (CreateBookingRequest.Slot s : slots) {
            BigDecimal price = s.price() != null ? s.price() : BigDecimal.ZERO;
            times.add(slotNode(s.time(), price));
        }
        return times;
    }

    private ObjectNode slotNode(int time, BigDecimal price) {
        ObjectNode slot = mapper.createObjectNode();
        slot.put("time", time);
        slot.set("players", mapper.createArrayNode());
        slot.put("id", 0);
        slot.put("hidden", false);
        slot.put("price", price);
        return slot;
    }

    /** Full request body from an already-persisted booking (used at fire time). */
    public String buildBodyJson(QueuedBooking b) {
        try {
            JsonNode players = mapper.readTree(b.getPlayersJson());
            ArrayNode times = resolveTimes(b);
            return buildBodyJson(b.getCourtId(), b.getPlayDate().toString(), times, players);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build reservation body for booking " + b.getId(), e);
        }
    }

    /** Full request body from explicit parts. */
    public String buildBodyJson(long courtId, String date, ArrayNode times, JsonNode players) {
        ObjectNode root = mapper.createObjectNode();
        root.put("date", date);
        root.set("times", times.deepCopy());
        root.set("players", players.deepCopy());
        root.put("repeat", 0);
        root.set("group", mapper.createObjectNode());
        root.put("every_other_week", false);
        root.put("court_id", courtId);
        root.put("name", "");
        root.put("monthly_prepayment", false);
        root.put("version", props.getReservationVersion());
        root.set("cart", mapper.createArrayNode());
        return root.toString();
    }

    /** Times for a stored booking: prefer the saved {@code slotsJson}, else rebuild a single slot. */
    private ArrayNode resolveTimes(QueuedBooking b) {
        if (b.getSlotsJson() != null && !b.getSlotsJson().isBlank()) {
            JsonNode parsed = mapper.readTree(b.getSlotsJson());
            if (parsed instanceof ArrayNode arr) return arr;
        }
        // Legacy single-slot row.
        ArrayNode times = mapper.createArrayNode();
        times.add(slotNode(b.getSlotTime(), b.getPrice() != null ? b.getPrice() : BigDecimal.ZERO));
        return times;
    }
}
