package org.padel.booker.booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.padel.booker.auth.AuthService;
import org.padel.booker.client.ApiResponse;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.AttemptOutcome;
import org.padel.booker.domain.BookingAttempt;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.repo.BookingAttemptRepository;
import org.padel.booker.repo.QueuedBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs the midnight burst for one queued booking: aligns to the server clock, waits until just
 * before the target instant, then fires create-reservation repeatedly until it succeeds or the
 * give-up window passes. Retrying is safe — a taken/too-early slot just errors, never double-books.
 */
@Component
public class BookingExecutor {

    private static final Logger log = LoggerFactory.getLogger(BookingExecutor.class);
    private static final int MAX_ATTEMPTS = 250;

    private final SportyApiClient api;
    private final AuthService auth;
    private final ReservationPayloadBuilder payloadBuilder;
    private final QueuedBookingRepository bookings;
    private final BookingAttemptRepository attempts;
    private final SportyProperties props;
    private final ObjectMapper mapper;

    public BookingExecutor(SportyApiClient api, AuthService auth, ReservationPayloadBuilder payloadBuilder,
                           QueuedBookingRepository bookings, BookingAttemptRepository attempts,
                           SportyProperties props, ObjectMapper mapper) {
        this.api = api;
        this.auth = auth;
        this.payloadBuilder = payloadBuilder;
        this.bookings = bookings;
        this.attempts = attempts;
        this.props = props;
        this.mapper = mapper;
    }

    public void run(long bookingId) {
        QueuedBooking booking = bookings.findById(bookingId).orElse(null);
        if (booking == null) {
            log.warn("Booking {} vanished before firing", bookingId);
            return;
        }
        if (booking.getStatus() != BookingStatus.PENDING) {
            log.info("Booking {} is {}, not firing", bookingId, booking.getStatus());
            return;
        }

        booking.setStatus(BookingStatus.RUNNING);
        bookings.save(booking);
        log.info("Firing booking {} (court {}, {} {}) target={}", bookingId,
                booking.getCourtId(), booking.getPlayDate(), booking.getSlotTimeFmt(), booking.getTargetFireAt());

        String body;
        try {
            body = payloadBuilder.buildBodyJson(booking);
        } catch (RuntimeException e) {
            fail(booking, "Could not build request body: " + e.getMessage());
            return;
        }

        long offset = safeOffset();
        long target = booking.getTargetFireAt().toEpochMilli();
        // Fire the first attempt firstAttemptAfterMs after the server's midnight (offset = serverMs - localMs).
        long fireStartLocal = target + props.getBooking().getFirstAttemptAfterMs() - offset;
        long giveUpLocal = target + props.getBooking().getGiveUpMs() - offset;

        waitUntil(fireStartLocal);

        String token = auth.getToken();
        int attemptNo = 0;
        boolean won = false;

        while (System.currentTimeMillis() <= giveUpLocal && attemptNo < MAX_ATTEMPTS) {
            attemptNo++;
            ApiResponse resp;
            try {
                resp = api.createReservation(body, token);
            } catch (RuntimeException e) {
                record(booking, attemptNo, body, null, "exception: " + e.getMessage(), AttemptOutcome.ERROR);
                sleep(props.getBooking().getIntervalMs());
                continue;
            }

            AttemptOutcome outcome = classify(resp);
            record(booking, attemptNo, body, resp.status(), resp.body(), outcome);

            if (outcome == AttemptOutcome.SUCCESS) {
                Long reservationId = parseReservationId(resp.body());
                booking.setStatus(BookingStatus.SUCCEEDED);
                booking.setCreatedReservationId(reservationId);
                booking.setNote("Reserved after " + attemptNo + " attempt(s), reservation " + reservationId);
                bookings.save(booking);
                log.info("Booking {} SUCCEEDED on attempt {} -> reservation {}", bookingId, attemptNo, reservationId);
                won = true;
                break;
            } else if (outcome == AttemptOutcome.AUTH_FAILED) {
                log.warn("Booking {} got auth failure on attempt {}, refreshing token", bookingId, attemptNo);
                try {
                    token = auth.refreshToken();
                } catch (RuntimeException e) {
                    log.error("Token refresh failed mid-burst: {}", e.getMessage());
                }
                // retry immediately, no sleep
            } else {
                sleep(props.getBooking().getIntervalMs());
            }
        }

        if (!won) {
            fail(booking, "Slot not secured after " + attemptNo + " attempt(s) within the give-up window");
            log.info("Booking {} FAILED after {} attempts", bookingId, attemptNo);
        }
    }

    private long safeOffset() {
        try {
            return api.measureServerOffsetMillis();
        } catch (RuntimeException e) {
            log.warn("Clock sync failed ({}); assuming zero offset", e.getMessage());
            return 0;
        }
    }

    private AttemptOutcome classify(ApiResponse resp) {
        if (resp.isSuccess()) return AttemptOutcome.SUCCESS;
        if (resp.status() == 401) return AttemptOutcome.AUTH_FAILED;
        String b = resp.body() == null ? "" : resp.body();
        if (resp.status() == 403 || b.contains("court_unavailable") || b.contains("unavailable")) {
            return AttemptOutcome.COURT_UNAVAILABLE;
        }
        return AttemptOutcome.ERROR;
    }

    private Long parseReservationId(String body) {
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode arr = node.path("reservations");
            if (arr.isArray() && !arr.isEmpty()) return arr.get(0).asLong();
        } catch (Exception ignored) {
        }
        return null;
    }

    private void record(QueuedBooking booking, int attemptNo, String body, Integer status,
                        String responseBody, AttemptOutcome outcome) {
        try {
            attempts.save(BookingAttempt.of(booking.getId(), attemptNo, body, status, responseBody, outcome));
        } catch (RuntimeException e) {
            log.warn("Failed to persist attempt for booking {}: {}", booking.getId(), e.getMessage());
        }
    }

    private void fail(QueuedBooking booking, String note) {
        booking.setStatus(BookingStatus.FAILED);
        booking.setNote(note);
        bookings.save(booking);
    }

    private void waitUntil(long localEpochMs) {
        long remaining = localEpochMs - System.currentTimeMillis();
        if (remaining <= 0) {
            log.info("Fire start already passed by {} ms; firing immediately", -remaining);
            return;
        }
        log.info("Waiting {} ms until fire start", remaining);
        // Coarse sleep until ~200ms out, then tight spin for precision.
        while (localEpochMs - System.currentTimeMillis() > 200) {
            sleep(50);
        }
        while (System.currentTimeMillis() < localEpochMs) {
            Thread.onSpinWait();
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
