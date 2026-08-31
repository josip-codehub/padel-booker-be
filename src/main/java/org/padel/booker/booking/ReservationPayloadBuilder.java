package org.padel.booker.booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.QueuedBooking;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Builds the POST /reservation body, mirroring exactly what the Sporty Plus web app sends.
 * The top-level "players" array is [ <yourTeam>, {} ] for a solo booking.
 */
@Component
public class ReservationPayloadBuilder {

    private final ObjectMapper mapper;
    private final SportyProperties props;

    public ReservationPayloadBuilder(ObjectMapper mapper, SportyProperties props) {
        this.mapper = mapper;
        this.props = props;
    }

    /** The players array to persist with a queued booking: [team, {}]. */
    public ArrayNode playersArray(JsonNode team) {
        ArrayNode arr = mapper.createArrayNode();
        arr.add(team.deepCopy());
        arr.add(mapper.createObjectNode()); // second slot, empty, as the web app sends
        return arr;
    }

    /** Full request body from an already-persisted booking (used at fire time). */
    public String buildBodyJson(QueuedBooking b) {
        try {
            JsonNode players = mapper.readTree(b.getPlayersJson());
            BigDecimal price = b.getPrice() != null ? b.getPrice() : BigDecimal.ZERO;
            return buildBodyJson(b.getCourtId(), b.getPlayDate().toString(), b.getSlotTime(), price, players);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build reservation body for booking " + b.getId(), e);
        }
    }

    /** Full request body from raw parts (used when computing the price at queue time). */
    public String buildBodyJson(long courtId, String date, int time, BigDecimal price, JsonNode players) {
        ObjectNode root = mapper.createObjectNode();
        root.put("date", date);

        ArrayNode times = mapper.createArrayNode();
        ObjectNode slot = mapper.createObjectNode();
        slot.put("time", time);
        slot.set("players", mapper.createArrayNode());
        slot.put("id", 0);
        slot.put("hidden", false);
        slot.put("price", price);
        times.add(slot);
        root.set("times", times);

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
}
