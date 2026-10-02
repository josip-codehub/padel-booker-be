package org.padel.booker.fixed;

import org.padel.booker.booking.SlotTimes;
import org.padel.booker.club.ClubRegistry;
import org.padel.booker.club.ClubService;
import org.padel.booker.booking.BookingScheduler;
import org.padel.booker.config.SportyProperties;
import org.padel.booker.domain.AppUser;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.FixedBooking;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.repo.AppUserRepository;
import org.padel.booker.repo.FixedBookingRepository;
import org.padel.booker.repo.QueuedBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Standing weekly ("fixed") bookings. Keeps the next couple of occurrences queued (each fires at the
 * club's midnight opening via the normal executor), reserves the slot against other users for every
 * future matching week, and lets an admin create/list/cancel rules.
 */
@Service
public class FixedBookingService {

    private static final Logger log = LoggerFactory.getLogger(FixedBookingService.class);
    private static final int OCCURRENCES_AHEAD = 2;

    private final FixedBookingRepository fixed;
    private final QueuedBookingRepository bookings;
    private final BookingScheduler scheduler;
    private final ClubService clubService;
    private final ClubRegistry registry;
    private final AppUserRepository users;
    private final ZoneId zone;

    public FixedBookingService(FixedBookingRepository fixed, QueuedBookingRepository bookings,
                               BookingScheduler scheduler, ClubService clubService, ClubRegistry registry,
                               AppUserRepository users, SportyProperties props) {
        this.fixed = fixed;
        this.bookings = bookings;
        this.scheduler = scheduler;
        this.clubService = clubService;
        this.registry = registry;
        this.users = users;
        this.zone = ZoneId.of(props.getTimezone());
    }

    // ---- creation / management -------------------------------------------------

    @Transactional
    public FixedBooking createRule(long userId, long clubId, long courtId, String courtName,
                                   LocalDate firstPlayDate, int slotMinutes, List<Integer> codes,
                                   String slotsJson, String playersJson, BigDecimal price) {
        FixedBooking r = new FixedBooking();
        r.setUserId(userId);
        r.setClubId(clubId);
        r.setCourtId(courtId);
        r.setCourtName(courtName);
        r.setWeekday(firstPlayDate.getDayOfWeek().getValue());
        r.setStartDate(firstPlayDate);
        r.setSlotTime(codes.get(0));
        r.setSlotTimeFmt(SlotTimes.fmt(codes.get(0)));
        r.setSlotEndFmt(SlotTimes.endFmt(codes, slotMinutes));
        r.setSlotCount(codes.size());
        r.setSlotMinutes(slotMinutes);
        r.setSlotTimesCsv(SlotTimes.csv(codes));
        r.setSlotsJson(slotsJson);
        r.setPlayersJson(playersJson);
        r.setPrice(price);
        r.setActive(true);
        FixedBooking saved = fixed.save(r);
        log.info("Created fixed booking {} (user {}) club {} court {} {} {}-{} x{}", saved.getId(), userId,
                clubId, courtId, DayOfWeek.of(saved.getWeekday()), saved.getSlotTimeFmt(), saved.getSlotEndFmt(),
                saved.getSlotCount());
        ensureOccurrences(saved);
        return saved;
    }

    public List<FixedBookingView> listForUser(long userId) {
        List<FixedBookingView> out = new ArrayList<>();
        for (FixedBooking r : fixed.findByUserIdOrderByCreatedAtDesc(userId)) {
            out.add(view(r));
        }
        return out;
    }

    @Transactional
    public void cancel(long userId, long ruleId) {
        FixedBooking r = fixed.findById(ruleId)
                .orElseThrow(() -> new IllegalArgumentException("No fixed booking " + ruleId));
        if (r.getUserId() == null || r.getUserId() != userId) {
            throw new IllegalArgumentException("No fixed booking " + ruleId);
        }
        r.setActive(false);
        fixed.save(r);
        // Drop the pending (not-yet-fired) occurrences; leave already-booked courts alone.
        for (QueuedBooking b : bookings.findByFixedBookingIdAndStatus(ruleId, BookingStatus.PENDING)) {
            scheduler.cancel(b.getId());
            b.setStatus(BookingStatus.CANCELLED);
            b.setNote("Fixed booking cancelled");
            bookings.save(b);
        }
        log.info("Cancelled fixed booking {} (user {})", ruleId, userId);
    }

    // ---- conflict + availability overlay --------------------------------------

    /** The owner's username if an active rule holds any of these slots on this date's weekday. */
    public Optional<String> conflictOwner(long courtId, LocalDate playDate, List<Integer> codes) {
        int weekday = playDate.getDayOfWeek().getValue();
        Set<Integer> want = new HashSet<>(codes);
        for (FixedBooking r : fixed.findByActiveTrueAndCourtIdAndWeekday(courtId, weekday)) {
            for (int c : r.slotCodes()) {
                if (want.contains(c)) return Optional.of(usernameOf(r.getUserId()));
            }
        }
        return Optional.empty();
    }

    /** All slots held by active rules within [from,to], for the grid overlay. */
    public List<HeldSlot> heldSlots(LocalDate from, LocalDate to) {
        List<HeldSlot> out = new ArrayList<>();
        Map<Long, String> names = usernames();
        for (FixedBooking r : fixed.findByActiveTrue()) {
            String uname = names.getOrDefault(r.getUserId(), "admin");
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (d.getDayOfWeek().getValue() != r.getWeekday()) continue;
                for (int code : r.slotCodes()) {
                    out.add(new HeldSlot(r.getCourtId(), d, code, r.getUserId(), uname));
                }
            }
        }
        return out;
    }

    // ---- reconciler ------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        try {
            reconcile();
        } catch (RuntimeException e) {
            log.warn("Fixed-booking reconcile on startup failed: {}", e.getMessage());
        }
    }

    /** Hourly: make sure the next couple of occurrences of each active rule are queued. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 120_000L)
    @Transactional
    public void reconcile() {
        List<FixedBooking> active = fixed.findByActiveTrue();
        if (active.isEmpty()) return;
        int spawned = 0;
        for (FixedBooking r : active) {
            for (LocalDate d : nextDates(r, OCCURRENCES_AHEAD)) {
                if (bookings.findByFixedBookingIdAndPlayDate(r.getId(), d).isEmpty()) {
                    spawnOccurrence(r, d);
                    spawned++;
                }
            }
        }
        if (spawned > 0) log.info("Fixed-booking reconcile queued {} occurrence(s)", spawned);
    }

    /** Ensure the next couple of occurrences of one rule are queued (used right after creation). */
    private void ensureOccurrences(FixedBooking r) {
        for (LocalDate d : nextDates(r, OCCURRENCES_AHEAD)) {
            if (bookings.findByFixedBookingIdAndPlayDate(r.getId(), d).isEmpty()) {
                spawnOccurrence(r, d);
            }
        }
    }

    private void spawnOccurrence(FixedBooking r, LocalDate playDate) {
        QueuedBooking b = new QueuedBooking();
        b.setUserId(r.getUserId());
        b.setClubId(r.getClubId());
        b.setCourtId(r.getCourtId());
        b.setCourtName(r.getCourtName());
        b.setPlayDate(playDate);
        b.setSlotTime(r.getSlotTime());
        b.setSlotTimeFmt(r.getSlotTimeFmt());
        b.setSlotEndFmt(r.getSlotEndFmt());
        b.setSlotCount(r.getSlotCount());
        b.setSlotMinutes(r.getSlotMinutes());
        b.setSlotTimesCsv(r.getSlotTimesCsv());
        b.setSlotsJson(r.getSlotsJson());
        b.setPlayersJson(r.getPlayersJson());
        b.setPrice(r.getPrice());
        b.setFixedBookingId(r.getId());
        b.setStatus(BookingStatus.PENDING);
        int span = clubService.spanDays(r.getUserId(), r.getClubId());
        b.setTargetFireAt(playDate.minusDays(span).atStartOfDay(zone).toInstant());
        QueuedBooking saved = bookings.save(b);
        scheduler.schedule(saved);
        log.info("Spawned occurrence {} for fixed booking {} on {}", saved.getId(), r.getId(), playDate);
    }

    private List<LocalDate> nextDates(FixedBooking r, int n) {
        LocalDate base = LocalDate.now(zone);
        if (r.getStartDate() != null && r.getStartDate().isAfter(base)) base = r.getStartDate();
        LocalDate d = base;
        while (d.getDayOfWeek().getValue() != r.getWeekday()) d = d.plusDays(1);
        List<LocalDate> out = new ArrayList<>();
        for (int i = 0; i < n; i++) out.add(d.plusWeeks(i));
        return out;
    }

    // ---- helpers ---------------------------------------------------------------

    private FixedBookingView view(FixedBooking r) {
        String clubName = registry.find(r.getClubId()).map(c -> c.name()).orElse("Club " + r.getClubId());
        String weekdayName = DayOfWeek.of(r.getWeekday()).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        return new FixedBookingView(r.getId(), r.getClubId(), clubName, r.getCourtId(), r.getCourtName(),
                r.getWeekday(), weekdayName, r.getSlotTimeFmt(), r.getSlotEndFmt(), r.getSlotCount(),
                r.getPrice(), r.isActive());
    }

    private Map<Long, String> usernames() {
        Map<Long, String> m = new HashMap<>();
        for (AppUser u : users.findAll()) m.put(u.getId(), u.getUsername());
        return m;
    }

    private String usernameOf(Long userId) {
        if (userId == null) return "an admin";
        return users.findById(userId).map(AppUser::getUsername).orElse("an admin");
    }
}
