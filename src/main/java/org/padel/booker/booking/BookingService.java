package org.padel.booker.booking;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.BookingAttempt;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.repo.BookingAttemptRepository;
import org.padel.booker.repo.QueuedBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final SportyApiClient api;
    private final ReservationPayloadBuilder payloadBuilder;
    private final QueuedBookingRepository bookings;
    private final BookingAttemptRepository attempts;
    private final BookingScheduler scheduler;
    private final SportyProperties props;
    private final ZoneId zone;

    public BookingService(SportyApiClient api, ReservationPayloadBuilder payloadBuilder,
                          QueuedBookingRepository bookings, BookingAttemptRepository attempts,
                          BookingScheduler scheduler, SportyProperties props) {
        this.api = api;
        this.payloadBuilder = payloadBuilder;
        this.bookings = bookings;
        this.attempts = attempts;
        this.scheduler = scheduler;
        this.props = props;
        this.zone = ZoneId.of(props.getTimezone());
    }

    public QueuedBooking queue(CreateBookingRequest req) {
        if (req.playDate().isBefore(LocalDate.now(zone))) {
            throw new IllegalArgumentException("Play date is in the past");
        }

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
        log.info("Queued booking {} for court {} on {} {} -> fires at {}", saved.getId(),
                saved.getCourtId(), saved.getPlayDate(), saved.getSlotTimeFmt(), targetFireAt);
        scheduler.schedule(saved);
        return saved;
    }

    public List<QueuedBooking> list() {
        return bookings.findAllByOrderByTargetFireAtDesc();
    }

    public QueuedBooking get(long id) {
        return bookings.findById(id).orElseThrow(() -> new IllegalArgumentException("No booking " + id));
    }

    public QueuedBooking cancel(long id) {
        QueuedBooking b = get(id);
        if (b.getStatus() != BookingStatus.PENDING) {
            throw new IllegalStateException("Only PENDING bookings can be cancelled (this one is " + b.getStatus() + ")");
        }
        scheduler.cancel(id);
        b.setStatus(BookingStatus.CANCELLED);
        b.setNote("Cancelled by user");
        return bookings.save(b);
    }

    public List<BookingAttempt> attemptsFor(long bookingId) {
        return attempts.findByBookingIdOrderByAttemptAtAsc(bookingId);
    }

    public List<BookingAttempt> recentAttempts() {
        return attempts.findTop500ByOrderByAttemptAtDesc();
    }

    /** The instant the play date opens for booking: 00:00 club-time, span days before. */
    private Instant computeFireInstant(LocalDate playDate) {
        LocalDate openDate = playDate.minusDays(props.getMaxReservationSpanDays());
        return openDate.atStartOfDay(zone).toInstant();
    }

    private BigDecimal totalPrice(JsonNode priceNode) {
        BigDecimal total = BigDecimal.ZERO;
        if (priceNode == null || !priceNode.isObject()) return total;
        for (Iterator<Map.Entry<String, JsonNode>> it = priceNode.properties().iterator(); it.hasNext(); ) {
            JsonNode v = it.next().getValue();
            if (v.isNumber()) total = total.add(v.decimalValue());
        }
        return total;
    }

    private static String formatSlot(int hhmm) {
        return String.format("%02d:%02d", hhmm / 100, hhmm % 100);
    }
}
