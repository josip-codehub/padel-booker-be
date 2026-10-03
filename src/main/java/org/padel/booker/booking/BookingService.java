package org.padel.booker.booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import org.padel.booker.auth.SportyAccountService;
import org.padel.booker.auth.UserContext;
import org.padel.booker.client.ApiResponse;
import org.padel.booker.client.ClubRef;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.club.ClubRegistry;
import org.padel.booker.club.ClubService;
import org.padel.booker.club.SupportedClub;
import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.AppUser;
import org.padel.booker.domain.BookingAttempt;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.FixedBooking;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.domain.Role;
import org.padel.booker.fixed.FixedBookingService;
import org.padel.booker.players.ReservationPlayerService;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ClubService clubService;
    private final ClubRegistry registry;
    private final FixedBookingService fixedBookings;
    private final ReservationPlayerService reservationPlayers;
    private final ZoneId zone;

    public BookingService(SportyApiClient api, SportyAccountService accounts, ReservationPayloadBuilder payloadBuilder,
                          QueuedBookingRepository bookings, BookingAttemptRepository attempts,
                          BookingScheduler scheduler, SportyProperties props, ObjectMapper mapper,
                          AppUserRepository users, ClubService clubService, ClubRegistry registry,
                          FixedBookingService fixedBookings, ReservationPlayerService reservationPlayers) {
        this.api = api;
        this.accounts = accounts;
        this.payloadBuilder = payloadBuilder;
        this.bookings = bookings;
        this.attempts = attempts;
        this.scheduler = scheduler;
        this.props = props;
        this.mapper = mapper;
        this.users = users;
        this.clubService = clubService;
        this.registry = registry;
        this.fixedBookings = fixedBookings;
        this.reservationPlayers = reservationPlayers;
        this.zone = ZoneId.of(props.getTimezone());
    }

    public boolean isWithinWindow(long userId, long clubId, LocalDate playDate) {
        LocalDate lastOpenDay = LocalDate.now(zone).plusDays(clubService.spanDays(userId, clubId));
        return !playDate.isAfter(lastOpenDay);
    }

    /** Context shared by queue/reserveNow: validated club, players, priced times. */
    private record Prepared(SupportedClub cfg, ClubRef club, int slotMinutes, List<Integer> codes,
                            ArrayNode players, ArrayNode times, BigDecimal price) {}

    private Prepared prepare(long userId, CreateBookingRequest req) {
        clubService.requireAccess(userId, req.clubId());
        SupportedClub cfg = registry.require(req.clubId());
        ClubRef club = cfg.ref();

        if (req.playDate().isBefore(LocalDate.now(zone))) {
            throw new IllegalArgumentException("Play date is in the past");
        }
        List<Integer> codes = req.codes();
        if (codes.isEmpty()) throw new IllegalArgumentException("No slots selected");
        if (codes.size() < cfg.minSlots()) {
            throw new IllegalArgumentException(cfg.name() + " bookings need at least " + cfg.minSlots()
                    + " consecutive slot(s)");
        }
        if (codes.size() > cfg.maxSlots()) {
            throw new IllegalArgumentException(cfg.name() + " bookings can span at most " + cfg.maxSlots() + " slot(s)");
        }
        int slotMinutes = (req.slotMinutes() != null && req.slotMinutes() > 0)
                ? req.slotMinutes() : cfg.defaultSlotMinutes();

        ensureSlotFree(req.courtId(), req.playDate(), codes);

        JsonNode me = api.me(club.origin());
        JsonNode team = me.path("user").path("team");
        if (team.isMissingNode() || team.isNull() || !team.has("id")) {
            throw new IllegalStateException("Could not resolve your team from /me");
        }
        List<Long> partnerIds = req.partnerPlayerIds() == null ? List.of() : req.partnerPlayerIds();
        if (partnerIds.size() > ReservationPlayerService.MAX_PARTNERS) {
            throw new IllegalArgumentException("At most " + ReservationPlayerService.MAX_PARTNERS + " other players");
        }
        ArrayNode players = payloadBuilder.playersArray(team, reservationPlayers.resolvePartnerTeams(club, partnerIds));
        ArrayNode times = payloadBuilder.buildTimes(req.slots());
        String probeBody = payloadBuilder.buildBodyJson(req.courtId(), req.playDate().toString(), times, players);
        BigDecimal price = totalPrice(api.price(club, probeBody));
        return new Prepared(cfg, club, slotMinutes, codes, players, times, price);
    }

    /** Schedule a booking to fire at the club's midnight opening (or create a standing weekly rule). */
    public QueuedBooking queue(CreateBookingRequest req) {
        long userId = UserContext.require();
        Prepared p = prepare(userId, req);

        if (req.isRepeatWeekly()) {
            requireAdmin(userId);
            FixedBooking rule = fixedBookings.createRule(userId, req.clubId(), req.courtId(), req.courtName(),
                    req.playDate(), p.slotMinutes(), p.codes(), p.times().toString(), p.players().toString(), p.price());
            // Return the earliest spawned occurrence so the caller sees a concrete booking.
            return bookings.findByFixedBookingIdAndStatus(rule.getId(), BookingStatus.PENDING).stream()
                    .min(Comparator.comparing(QueuedBooking::getTargetFireAt))
                    .orElseThrow(() -> new IllegalStateException("Fixed booking created but no occurrence queued"));
        }

        QueuedBooking b = newBooking(userId, req, p);
        b.setTargetFireAt(computeFireInstant(userId, req.clubId(), req.playDate()));
        QueuedBooking saved = bookings.save(b);
        log.info("Queued booking {} (user {}) club {} court {} {} {}-{} -> fires {}", saved.getId(), userId,
                req.clubId(), saved.getCourtId(), saved.getPlayDate(), saved.getSlotTimeFmt(), saved.getSlotEndFmt(),
                saved.getTargetFireAt());
        scheduler.schedule(saved);
        return saved;
    }

    public ReserveNowResult reserveNow(CreateBookingRequest req) {
        long userId = UserContext.require();
        Prepared p = prepare(userId, req);

        String finalBody = payloadBuilder.buildBodyJson(req.courtId(), req.playDate().toString(), p.times(), p.players());
        String token = accounts.getToken(userId);
        ApiResponse resp = api.createReservation(p.club(), finalBody, token);
        if (resp.status() == 401) {
            token = accounts.refreshToken(userId);
            resp = api.createReservation(p.club(), finalBody, token);
        }
        if (resp.isSuccess()) {
            Long reservationId = parseReservationId(resp.body());
            log.info("Immediate reservation (user {}): club {} court {} {} {} -> {}", userId, req.clubId(),
                    req.courtId(), req.playDate(), SlotTimes.fmt(p.codes().get(0)), reservationId);
            return new ReserveNowResult(true, reservationId, p.price(),
                    reservationId != null ? "Reserved (reservation #" + reservationId + ")" : "Reserved");
        }
        String message = humanError(resp);
        log.info("Immediate reservation failed for user {} ({}): {}", userId, resp.status(), message);
        return new ReserveNowResult(false, null, p.price(), message);
    }

    private QueuedBooking newBooking(long userId, CreateBookingRequest req, Prepared p) {
        QueuedBooking b = new QueuedBooking();
        b.setUserId(userId);
        b.setClubId(req.clubId());
        b.setCourtId(req.courtId());
        b.setCourtName(req.courtName());
        b.setPlayDate(req.playDate());
        b.setSlotTime(p.codes().get(0));
        b.setSlotTimeFmt(SlotTimes.fmt(p.codes().get(0)));
        b.setSlotEndFmt(SlotTimes.endFmt(p.codes(), p.slotMinutes()));
        b.setSlotCount(p.codes().size());
        b.setSlotMinutes(p.slotMinutes());
        b.setSlotTimesCsv(SlotTimes.csv(p.codes()));
        b.setSlotsJson(p.times().toString());
        b.setPrice(p.price());
        b.setPlayersJson(p.players().toString());
        b.setStatus(BookingStatus.PENDING);
        return b;
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

    /** Reject if any requested slot overlaps a pending queue entry or an admin's standing weekly booking. */
    private void ensureSlotFree(long courtId, LocalDate playDate, List<Integer> codes) {
        Set<Integer> want = new HashSet<>(codes);
        for (QueuedBooking existing : bookings.findByStatusAndCourtIdAndPlayDate(BookingStatus.PENDING, courtId, playDate)) {
            for (int c : existing.slotCodes()) {
                if (want.contains(c)) {
                    String who = existing.getUserId() == null ? "another user"
                            : users.findById(existing.getUserId()).map(AppUser::getUsername).orElse("another user");
                    throw new IllegalStateException("That slot is already scheduled by " + who);
                }
            }
        }
        fixedBookings.conflictOwner(courtId, playDate, codes).ifPresent(owner -> {
            throw new IllegalStateException("This slot is a standing weekly booking by " + owner);
        });
    }

    private void requireAdmin(long userId) {
        AppUser u = users.findById(userId).orElseThrow(() -> new IllegalArgumentException("No user " + userId));
        if (u.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("Only admins can create fixed (recurring) bookings");
        }
    }

    private Instant computeFireInstant(long userId, long clubId, LocalDate playDate) {
        LocalDate openDate = playDate.minusDays(clubService.spanDays(userId, clubId));
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
}
