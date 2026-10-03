package org.padel.booker.players;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.padel.booker.auth.UserContext;
import org.padel.booker.client.ClubRef;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.club.ClubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Adds/removes people on a booking. Sporty models participants as the top-level {@code players} array
 * (one 1-player "team" object each). Add-while-booking is handled by the booking flow; this service
 * handles searching people and editing players on an already-booked reservation.
 *
 * Add/remove after booking works by fetching the reservation's editable object (it's embedded in the
 * court's {@code parsed_reservations} grid), modifying {@code players}, and PUTting it back — exactly
 * what the web app does.
 */
@Service
public class ReservationPlayerService {

    private static final Logger log = LoggerFactory.getLogger(ReservationPlayerService.class);
    public static final int MAX_PARTNERS = 3; // padel = 4 participants total

    private final SportyApiClient api;
    private final ClubService clubs;
    private final ObjectMapper mapper;

    public ReservationPlayerService(SportyApiClient api, ClubService clubs, ObjectMapper mapper) {
        this.api = api;
        this.clubs = clubs;
        this.mapper = mapper;
    }

    /** Search people by name within a club. */
    public List<PlayerView> search(long clubId, String query) {
        clubs.requireAccess(UserContext.require(), clubId);
        ClubRef club = clubs.ref(clubId);
        JsonNode resp = api.searchTeams(club, query);
        JsonNode data = resp != null && resp.has("data") ? resp.get("data") : resp;
        List<PlayerView> out = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode t : data) {
                long teamId = t.path("id").asLong();
                long playerId = t.path("players").path(0).path("id").asLong(teamId);
                out.add(new PlayerView(teamId, playerId, t.path("display_name").asText(null),
                        t.path("image").asText(null)));
            }
        }
        return out;
    }

    /** The team objects (for a reservation's players[]) for the given player ids. */
    public List<JsonNode> resolvePartnerTeams(ClubRef club, List<Long> playerIds) {
        List<JsonNode> teams = new ArrayList<>();
        if (playerIds == null) return teams;
        for (Long pid : playerIds) {
            JsonNode team = api.getTeamByPlayerId(club, pid);
            if (team == null) throw new IllegalArgumentException("Could not find player " + pid + " in this club");
            teams.add(team);
        }
        return teams;
    }

    public List<ReservationPlayer> getPlayers(long clubId, long reservationId, long courtId, LocalDate date) {
        clubs.requireAccess(UserContext.require(), clubId);
        return readPlayers(fetchReservation(clubs.ref(clubId), reservationId, courtId, date));
    }

    public List<ReservationPlayer> addPlayers(long clubId, long reservationId, long courtId, LocalDate date,
                                              List<Long> playerIds) {
        clubs.requireAccess(UserContext.require(), clubId);
        ClubRef club = clubs.ref(clubId);
        ObjectNode reservation = fetchReservation(club, reservationId, courtId, date);
        ArrayNode players = ensurePlayers(reservation);
        for (JsonNode team : resolvePartnerTeams(club, playerIds)) {
            players.add(team.deepCopy());
        }
        reservation.set("players", players);
        putAndLog(club, reservationId, reservation);
        return readPlayers(fetchReservation(club, reservationId, courtId, date));
    }

    public List<ReservationPlayer> removePlayer(long clubId, long reservationId, long courtId, LocalDate date,
                                                long playerId) {
        clubs.requireAccess(UserContext.require(), clubId);
        ClubRef club = clubs.ref(clubId);
        ObjectNode reservation = fetchReservation(club, reservationId, courtId, date);
        ArrayNode players = ensurePlayers(reservation);
        ArrayNode kept = mapper.createArrayNode();
        for (JsonNode p : players) {
            if (playerIdOf(p) != playerId) kept.add(p);
        }
        reservation.set("players", kept);
        putAndLog(club, reservationId, reservation);
        return readPlayers(fetchReservation(club, reservationId, courtId, date));
    }

    // --- helpers ---

    private void putAndLog(ClubRef club, long reservationId, ObjectNode reservation) {
        if (!reservation.has("group") || reservation.get("group").isNull()) {
            reservation.set("group", mapper.createObjectNode()); // the web app always sends group
        }
        api.putReservation(club, reservationId, reservation.toString());
        log.info("Updated players on reservation {} (club {})", reservationId, club.clubId());
    }

    /** The reservation's editable object, read from the court-day parsed_reservations grid. */
    private ObjectNode fetchReservation(ClubRef club, long reservationId, long courtId, LocalDate date) {
        JsonNode resp = api.getCourtDay(club, courtId, date);
        JsonNode court = resp != null && resp.has("data") ? resp.get("data") : resp;
        JsonNode parsed = court != null ? court.path("parsed_reservations") : null;
        if (parsed != null && parsed.isObject()) {
            for (var e : parsed.properties()) {
                JsonNode slot = e.getValue();
                if (slot.path("id").asLong(0) == reservationId) {
                    return (ObjectNode) slot.deepCopy();
                }
            }
        }
        throw new IllegalArgumentException("Reservation " + reservationId + " not found on court " + courtId
                + " for " + date);
    }

    private ArrayNode ensurePlayers(ObjectNode reservation) {
        JsonNode players = reservation.get("players");
        if (players instanceof ArrayNode arr) return arr;
        ArrayNode fresh = mapper.createArrayNode();
        reservation.set("players", fresh);
        return fresh;
    }

    private List<ReservationPlayer> readPlayers(ObjectNode reservation) {
        List<ReservationPlayer> out = new ArrayList<>();
        for (JsonNode p : reservation.path("players")) {
            out.add(new ReservationPlayer(playerIdOf(p), p.path("display_name").asText(null),
                    p.path("image").asText(null)));
        }
        return out;
    }

    /** A player entry is either {type:player, id:playerId} or a team object {players:[{id:playerId}]}. */
    private long playerIdOf(JsonNode p) {
        if ("team".equals(p.path("type").asText()) || p.path("players").isArray()) {
            long nested = p.path("players").path(0).path("id").asLong(0);
            if (nested != 0) return nested;
        }
        return p.path("id").asLong(0);
    }
}
