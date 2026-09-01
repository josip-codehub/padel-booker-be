package org.padel.booker.booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import org.padel.booker.auth.SportyAccountService;
import org.padel.booker.auth.UserContext;
import org.padel.booker.client.ApiResponse;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.BookingAttempt;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.repo.AppUserRepository;
import org.padel.booker.repo.BookingAttemptRepository;
import org.padel.booker.repo.QueuedBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final SportyApiClient api;
    private final SportyAccountService accounts;
    private final ReservationPayloadBuilder payloadBuilder;
    private final QueuedBookingRepository bookings;
    private final BookingAttemptRepository attempts;
    private final BookingScheduler scheduler;
    private final SportyProperties props;
    private final ObjectMapper mapper;
    private final AppUserRepository users;
    private final ZoneId zone;

    public BookingService(SportyApiClient api, SportyAccountService accounts, ReservationPayloadBuilder payloadBuilder,
                          QueuedBookingRepository bookings, BookingAttemptRepository attempts,
                          BookingScheduler scheduler, SportyProperties props, ObjectMapper mapper,
                          AppUserRepository users) {
        this.api = api;
        this.accounts = accounts;
        this.payloadBuilder = payloadBuilder;
        this.bookings = bookings;
        this.attempts = attempts;
        this.scheduler = scheduler;
        this.props = props;
        this.mapper = mapper;
        this.users = users;
        this.zone = ZoneId.of(props.getTimezone());
    }

    public boolean isWithinWindow(LocalDate playDate) {
        LocalDate lastOpenDay = LocalDate.now(zone).plusDays(props.getMaxReservationSpanDays());
        return !playDate.isAfter(lastOpenDay);
    }

    public QueuedBooking queue(CreateBookingRequest req) {
        long userId = UserContext.require();
        if (req.playDate().isBefore(LocalDate.now(zone))) {
            throw new IllegalArgumentException("Play date is in the past");
        }
        ensureNotAlreadyScheduled(req.courtId(), req.playDate(), req.slotTime());

        JsonNode me = api.me();
        JsonNode team = me.path("user").path("team");
        if (team.isMissingNode() || team.isNull() || !team.has("id")) {
            throw new IllegalStateException("Could not resolve your team from /me");
        }

        ArrayNode players = payloadBuilder.playersArray(team);
        String probeBody = payloadBuilder.buildBodyJson(
                req.courtId(), req.playDate().toString(), req.slotTime(), BigDecimal.ZERO, players);
        BigDecimal price = totalPrice(api.price(probeBody));
        Instant targetFireAt = computeFireInstant(req.playDate());

        QueuedBooking b = new QueuedBooking();
        b.setUserId(userId);
        b.setCourtId(req.courtId());
        b.setCourtName(req.courtName());
        b.setPlayDate(req.playDate());
        b.setSlotTime(req.slotTime());
        b.setSlotTimeFmt(formatSlot(req.slotTime()));
        b.setPrice(price);
        b.setPlayersJson(players.toString());
        b.setTargetFireAt(targetFireAt);
        b.setStatus(BookingStatus.PENDING);

        QueuedBooking saved = bookings.save(b);
        log.info("Queued booking {} (user {}) court {} {} {} -> fires {}", saved.getId(), userId,
                saved.getCourtId(), saved.getPlayDate(), saved.getSlotTimeFmt(), targetFireAt);
        scheduler.schedule(saved);
        return saved;
    }

    public ReserveNowResult reserveNow(CreateBookingRequest req) {
        long userId = UserContext.require();
        if (req.playDate().isBefore(LocalDate.now(zone))) {
            throw new IllegalArgumentException("Play date is in the past");
        }
        ensureNotAlreadyScheduled(req.courtId(), req.playDate(), req.slotTime());

        JsonNode me = api.me();
        JsonNode team = me.path("user").path("team");
        if (team.isMissingNode() || team.isNull() || !team.has("id")) {
            throw new IllegalStateException("Could not resolve your team from /me");
        }

        ArrayNode players = payloadBuilder.playersArray(team);
        String probeBody = payloadBuilder.buildBodyJson(
                req.courtId(), req.playDate().toString(), req.slotTime(), BigDecimal.ZERO, players);
        BigDecimal price = totalPrice(api.price(probeBody));
        String body = payloadBuilder.buildBodyJson(
                req.courtId(), req.playDate().toString(), req.slotTime(), price, players);

        String token = accounts.getToken(userId);
        ApiResponse resp = api.createReservation(body, token);
        if (resp.status() == 401) {
            token = accounts.refreshToken(userId);
            resp = api.createReservation(body, token);
        }

        if (resp.isSuccess()) {
            Long reservationId = parseReservationId(resp.body());
            log.info("Immediate reservation (user {}): court {} {} {} -> {}", userId,
                    req.courtId(), req.playDate(), formatSlot(req.slotTime()), reservationId);
            return new ReserveNowResult(true, reservationId, price,
                    reservationId != null ? "Reserved (reservation #" + reservationId + ")" : "Reserved");
        }
        String message = humanError(resp);
        log.info("Immediate reservation failed for user {} ({}): {}", userId, resp.status(), message);
        return new ReserveNowResult(false, null, price, message);
    }

    public List<QueuedBooking> list() {
        return bookings.findByUserIdOrderByTargetFireAtDesc(UserContext.require());
    }

    public QueuedBooking get(long id) {
        return bookings.findById(id).orElseThrow(() -> new IllegalArgumentException("No booking " + id));
    }

    public QueuedBooking cancel(long id) {
        QueuedBooking b = getOwned(id);
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only PENDING bookings can be cancelled (this one is " + b.getStatus() + ")");
        }
        scheduler.cancel(id);
        b.setStatus(BookingStatus.CANCELLED);
        b.setNote("Cancelled by user");
        return bookings.save(b);
    }

    public List<BookingAttempt> attemptsFor(long bookingId) {
        getOwned(bookingId);
        return attempts.findByBookingIdOrderByAttemptAtAsc(bookingId);
    }

    public List<BookingAttempt> recentAttempts() {
        List<Long> ids = bookings.findByUserIdOrderByTargetFireAtDesc(UserContext.require())
                .stream().map(QueuedBooking::getId).toList();
        if (ids.isEmpty()) return List.of();
        return attempts.findTop500ByBookingIdInOrderByAttemptAtDesc(ids);
    }

    private QueuedBooking getOwned(long id) {
        QueuedBooking b = get(id);
        Long uid = UserContext.get();
        if (uid == null || !uid.equals(b.getUserId())) {
            throw new IllegalArgumentException("No booking " + id);
        }
        return b;
    }

    private void ensureNotAlreadyScheduled(long courtId, LocalDate playDate, int slotTime) {
        bookings.findFirstByStatusAndCourtIdAndPlayDateAndSlotTime(BookingStatus.PENDING, courtId, playDate, slotTime)
                .ifPresent(existing -> {
                    String who = users.findById(existing.getUserId() == null ? -1 : existing.getUserId())
                            .map(u -> u.getUsername()).orElse("another user");
                    throw new IllegalStateException("That slot is already scheduled by " + who);
                });
    }

    private Instant computeFireInstant(LocalDate playDate) {
        LocalDate openDate = playDate.minusDays(props.getMaxReservationSpanDays());
        return openDate.atStartOfDay(zone).toInstant();
    }

    private BigDecimal totalPrice(JsonNode priceNode) {
        BigDecimal total = BigDecimal.ZERO;
        if (priceNode == null || !priceNode.isObject()) return total;
        for (Map.Entry<String, JsonNode> e : priceNode.properties()) {
            if (e.getValue().isNumber()) total = total.add(e.getValue().decimalValue());
        }
        return total;
    }

    private Long parseReservationId(String body) {
        try {
            JsonNode arr = mapper.readTree(body).path("reservations");
            if (arr.isArray() && !arr.isEmpty()) return arr.get(0).asLong();
        } catch (Exception ignored) {
        }
        return null;
    }

    private String humanError(ApiResponse resp) {
        String b = resp.body() == null ? "" : resp.body();
        if (resp.status() == 403 || b.contains("court_unavailable") || b.contains("unavailable")) {
            return "Court is no longer available (already taken).";
        }
        try {
            JsonNode msg = mapper.readTree(b).path("message");
            if (!msg.isMissingNode() && !msg.isNull()) return msg.asText();
        } catch (Exception ignored) {
        }
        return "Booking failed (HTTP " + resp.status() + ").";
    }

    private static String formatSlot(int timeCode) {
        int hours = timeCode / 100;
        int minutes = Math.round((timeCode % 100) * 0.6f);
        return String.format("%02d:%02d", hours, minutes);
    }
}
