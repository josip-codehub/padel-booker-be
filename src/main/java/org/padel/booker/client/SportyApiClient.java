package org.padel.booker.client;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import org.padel.booker.auth.SportyAccountService;
import org.padel.booker.auth.UserContext;
import org.padel.booker.config.SportyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.Function;

/**
 * Calls the tenis.plus data API. Read calls auto-refresh the token once on 401.
 * The create-reservation call is raw (returns status+body) so the burst executor stays in control.
 */
@Component
public class SportyApiClient {

    private static final Logger log = LoggerFactory.getLogger(SportyApiClient.class);

    private final RestClient api;
    private final SportyAccountService accounts;
    private final SportyProperties props;

    public SportyApiClient(@Qualifier("apiRestClient") RestClient api,
                           SportyAccountService accounts,
                           SportyProperties props) {
        this.api = api;
        this.accounts = accounts;
        this.props = props;
    }

    /** GET /me — also the token health check. Uses the default (Modesto) origin. */
    public JsonNode me() {
        return me(props.getOrigin());
    }

    /**
     * GET /me with an explicit {@code origin} header (null = client default). The membership block in
     * the response (e.g. {@code max_reservation_span}) is scoped to the origin's club.
     */
    public JsonNode me(String origin) {
        return withAuthRetry(token -> api.get()
                .uri("/me")
                .headers(h -> {
                    h.set(HttpHeaders.AUTHORIZATION, bearer(token));
                    if (origin != null) h.set("origin", origin);
                })
                .retrieve()
                .body(JsonNode.class));
    }

    /** The logged-in player's id (from /me). */
    public long currentPlayerId() {
        return me().path("user").path("id").asLong();
    }

    /**
     * A player's own reservations from a given date onward (upcoming bookings), across all pages.
     * Returns an array of reservation items (unwrapped from the {data,...} envelope).
     */
    public JsonNode getPlayerReservations(long playerId, LocalDate from) {
        ArrayNode all = JsonNodeFactory.instance.arrayNode();
        int page = 1;
        int lastPage;
        do {
            final int p = page;
            JsonNode resp = withAuthRetry(token -> api.get()
                    .uri(b -> b.path("/reservation")
                            .queryParam("filter[player]", playerId)
                            .queryParam("filter[from]", from.toString())
                            .queryParam("with_reservation_lengths", true)
                            .queryParam("limit", 1000)
                            .queryParam("page", p)
                            .build())
                    // Portal origin so the player's reservations across ALL clubs are returned, not just one.
                    .headers(h -> auth(h, token, props.getPortalOrigin()))
                    .retrieve()
                    .body(JsonNode.class));
            if (resp == null) break;
            JsonNode data = resp.has("data") ? resp.get("data") : resp;
            if (data.isArray()) {
                data.forEach(all::add);
            }
            lastPage = resp.path("meta").path("last_page").asInt(1);
            page++;
        } while (page <= lastPage && page <= 50);
        return all;
    }

    /** Active courts for a club, including working_hours. */
    public JsonNode getCourts(ClubRef club) {
        return withAuthRetry(token -> api.get()
                .uri(b -> b.path("/court")
                        .queryParam("filter[club_id]", club.clubId())
                        .queryParam("filter[is_active]", 1)
                        .build())
                .headers(h -> auth(h, token, club.origin()))
                .retrieve()
                .body(JsonNode.class));
    }

    /**
     * Courts with the server-precomputed availability grid for a date range: each court carries a
     * {@code parsed_reservations} map (date -> timeCode -> slot). This is exactly what the web app's
     * week view uses. Free slots have {@code id:0} and a {@code price}; booked slots have a non-zero
     * {@code id} plus {@code players}. One call covers all courts and the whole week.
     *
     * Note: slot time codes are decimal-hours x100 (07:00=700, 14:30=1450, 20:30=2050).
     */
    public JsonNode getCourtsWithAvailability(ClubRef club, LocalDate from, LocalDate to) {
        return withAuthRetry(token -> api.get()
                .uri(b -> b.path("/court")
                        .queryParam("filter[is_active]", 1)
                        .queryParam("filter[club_id]", club.clubId())
                        .queryParam("from_date", from.toString())
                        .queryParam("to_date", to.toString())
                        .queryParam("parsed2", true)
                        .build())
                .headers(h -> auth(h, token, club.origin()))
                .retrieve()
                .body(JsonNode.class));
    }

    /** POST /reservation/price — returns {"<playerId>": price}. */
    public JsonNode price(ClubRef club, String bodyJson) {
        return withAuthRetry(token -> api.post()
                .uri("/reservation/price")
                .headers(h -> auth(h, token, club.origin()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyJson)
                .retrieve()
                .body(JsonNode.class));
    }

    /** Raw create — never throws on 4xx; the burst executor interprets the result. */
    public ApiResponse createReservation(ClubRef club, String bodyJson, String token) {
        return api.post()
                .uri("/reservation")
                .headers(h -> auth(h, token, club.origin()))
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyJson)
                .exchange((req, res) -> new ApiResponse(res.getStatusCode().value(), readBody(res.getBody())));
    }

    /** DELETE /reservation/{id}. */
    public void deleteReservation(ClubRef club, long reservationId) {
        withAuthRetry(token -> {
            api.delete()
                    .uri("/reservation/{id}", reservationId)
                    .headers(h -> auth(h, token, club.origin()))
                    .retrieve()
                    .toBodilessEntity();
            return null;
        });
    }

    /**
     * Offset in ms between the upstream server clock and this machine's clock
     * (serverMillis - localMillis). Positive means the server is ahead of us.
     * Uses the Date response header; corrected for round-trip midpoint.
     */
    public long measureServerOffsetMillis(String origin) {
        String token = accounts.getToken(UserContext.require());
        long t0 = System.currentTimeMillis();
        Instant[] serverInstant = new Instant[1];
        api.get()
                .uri("/me")
                .headers(h -> auth(h, token, origin))
                .exchange((req, res) -> {
                    String date = res.getHeaders().getFirst(HttpHeaders.DATE);
                    if (date != null) {
                        serverInstant[0] = ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                    }
                    return null;
                });
        long t1 = System.currentTimeMillis();
        if (serverInstant[0] == null) {
            log.warn("No Date header from server; assuming zero clock offset");
            return 0;
        }
        long localMidpoint = (t0 + t1) / 2;
        long offset = serverInstant[0].toEpochMilli() - localMidpoint;
        log.info("Measured server clock offset: {} ms (round-trip {} ms)", offset, (t1 - t0));
        return offset;
    }

    private static final int MAX_IO_RETRIES = 2;
    private static final long IO_RETRY_BACKOFF_MS = 600;

    /**
     * Runs a read call with two safety nets: refresh-and-retry once on 401, and retry a few times on
     * transient I/O errors (the upstream is flaky under load). Only used for idempotent reads — the
     * create-reservation call is intentionally NOT retried, since a timed-out create may have
     * actually succeeded server-side.
     */
    private <T> T withAuthRetry(Function<String, T> call) {
        long userId = UserContext.require();
        int ioAttempts = 0;
        while (true) {
            try {
                return call.apply(accounts.getToken(userId));
            } catch (HttpClientErrorException.Unauthorized e) {
                log.info("Got 401 for user {}, refreshing token and retrying once", userId);
                return call.apply(accounts.refreshToken(userId));
            } catch (ResourceAccessException e) {
                ioAttempts++;
                if (ioAttempts > MAX_IO_RETRIES) {
                    log.warn("I/O error after {} retries, giving up: {}", MAX_IO_RETRIES, e.getMessage());
                    throw e;
                }
                log.warn("Transient I/O error ({}), retry {}/{}", e.getMessage(), ioAttempts, MAX_IO_RETRIES);
                sleep(IO_RETRY_BACKOFF_MS);
            }
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void auth(HttpHeaders h, String token, String origin) {
        h.set(HttpHeaders.AUTHORIZATION, bearer(token));
        if (origin != null) h.set("origin", origin);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String readBody(InputStream in) {
        if (in == null) return "";
        try (in) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }
}
