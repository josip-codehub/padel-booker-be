package org.padel.booker.client;

import tools.jackson.databind.JsonNode;
import org.padel.booker.auth.AuthService;
import org.padel.booker.config.SportyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.HttpClientErrorException;

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
    private final AuthService auth;
    private final SportyProperties props;

    public SportyApiClient(@Qualifier("apiRestClient") RestClient api,
                           AuthService auth,
                           SportyProperties props) {
        this.api = api;
        this.auth = auth;
        this.props = props;
    }

    /** GET /me — also the token health check. */
    public JsonNode me() {
        return withAuthRetry(token -> api.get()
                .uri("/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(JsonNode.class));
    }

    /** Active courts for the configured club, including working_hours. */
    public JsonNode getCourts() {
        return withAuthRetry(token -> api.get()
                .uri(b -> b.path("/court")
                        .queryParam("filter[club_id]", props.getClubId())
                        .queryParam("filter[is_active]", 1)
                        .build())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(JsonNode.class));
    }

    /** All reservations in the club between two dates (inclusive), optionally one court. */
    public JsonNode getReservations(LocalDate from, LocalDate to, Long courtId) {
        return withAuthRetry(token -> api.get()
                .uri(b -> {
                    b.path("/reservation")
                            .queryParam("filter[club]", props.getClubId())
                            .queryParam("filter[from]", from.toString())
                            .queryParam("filter[to]", to.toString());
                    if (courtId != null) {
                        b.queryParam("filter[court]", courtId);
                    }
                    return b.build();
                })
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .retrieve()
                .body(JsonNode.class));
    }

    /** POST /reservation/price — returns {"<playerId>": price}. */
    public JsonNode price(String bodyJson) {
        return withAuthRetry(token -> api.post()
                .uri("/reservation/price")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyJson)
                .retrieve()
                .body(JsonNode.class));
    }

    /** Raw create — never throws on 4xx; the burst executor interprets the result. */
    public ApiResponse createReservation(String bodyJson, String token) {
        return api.post()
                .uri("/reservation")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .body(bodyJson)
                .exchange((req, res) -> new ApiResponse(res.getStatusCode().value(), readBody(res.getBody())));
    }

    /** DELETE /reservation/{id}. */
    public void deleteReservation(long reservationId) {
        withAuthRetry(token -> {
            api.delete()
                    .uri("/reservation/{id}", reservationId)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
    public long measureServerOffsetMillis() {
        String token = auth.getToken();
        long t0 = System.currentTimeMillis();
        Instant[] serverInstant = new Instant[1];
        api.get()
                .uri("/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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

    private <T> T withAuthRetry(Function<String, T> call) {
        try {
            return call.apply(auth.getToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            log.info("Got 401, refreshing token and retrying once");
            return call.apply(auth.refreshToken());
        }
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
