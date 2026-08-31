package org.padel.booker.mine;

import org.padel.booker.client.SportyApiClient;
import org.padel.booker.config.SportyProperties;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class MyReservationsService {

    private final SportyApiClient api;
    private final ZoneId zone;

    public MyReservationsService(SportyApiClient api, SportyProperties props) {
        this.api = api;
        this.zone = ZoneId.of(props.getTimezone());
    }

    /** The logged-in player's reservations from the given date onward (default: today). */
    public List<MyReservationView> list(LocalDate from) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.now(zone);
        long playerId = api.currentPlayerId();
        JsonNode items = api.getPlayerReservations(playerId, effectiveFrom);

        List<MyReservationView> out = new ArrayList<>();
        for (JsonNode r : items) {
            out.add(map(r, playerId));
        }
        out.sort(Comparator.comparing(MyReservationView::date).thenComparing(MyReservationView::fromTime));
        return out;
    }

    public void cancel(long reservationId) {
        api.deleteReservation(reservationId);
    }

    private MyReservationView map(JsonNode r, long playerId) {
        long id = r.path("id").asLong();
        JsonNode court = r.path("court");
        long courtId = court.path("id").asLong();
        String courtName = court.path("name").asText(null);

        String fromStr = r.path("from").asText(null);
        String date = fromStr != null ? Instant.parse(fromStr).atZone(zone).toLocalDate().toString() : null;

        List<String> players = new ArrayList<>();
        for (JsonNode p : r.path("players")) {
            String name = p.path("display_name").asText(null);
            if (name != null) players.add(name);
        }

        BigDecimal total = BigDecimal.ZERO;
        for (JsonNode price : r.path("prices")) {
            if (price.isNumber()) total = total.add(price.decimalValue());
        }

        boolean createdByMe = r.path("created_by").path("id").asLong() == playerId;
        boolean recurring = r.hasNonNull("series");

        return new MyReservationView(
                id,
                courtId,
                courtName,
                date,
                r.path("from_time_fmt").asText(null),
                r.path("to_time_fmt").asText(null),
                players,
                total,
                r.path("is_paid").asBoolean(false),
                r.path("needs_payment").asBoolean(false),
                createdByMe,
                recurring
        );
    }
}
