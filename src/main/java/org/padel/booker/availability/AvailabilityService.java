package org.padel.booker.availability;

import org.padel.booker.auth.UserContext;
import org.padel.booker.client.ClubRef;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.club.ClubRegistry;
import org.padel.booker.club.ClubService;
import org.padel.booker.club.ClubView;
import org.padel.booker.domain.AppUser;
import org.padel.booker.domain.BookingStatus;
import org.padel.booker.domain.QueuedBooking;
import org.padel.booker.fixed.FixedBookingService;
import org.padel.booker.mine.MyReservationView;
import org.padel.booker.mine.MyReservationsService;
import org.padel.booker.repo.AppUserRepository;
import org.padel.booker.repo.QueuedBookingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/**
 * Builds the availability grid for one or more clubs. Each club's courts come from the upstream's
 * server-precomputed {@code parsed_reservations} (one live call per club, all its courts, whole week);
 * the per-club calls run in parallel because the upstream is slow. Overlays: slots reserved by the
 * logged-in player on Sporty ({@code mine}), future slots scheduled in our Queue by any app user
 * ({@code queued}), and slots held by an admin's standing weekly booking.
 *
 * Slot time codes are decimal-hours x100: 700=07:00, 850=08:30, 1450=14:30, 2050=20:30.
 */
@Service
public class AvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private final SportyApiClient api;
    private final QueuedBookingRepository queuedBookings;
    private final MyReservationsService myReservations;
    private final AppUserRepository users;
    private final ClubService clubs;
    private final ClubRegistry registry;
    private final FixedBookingService fixedBookings;
    private final ExecutorService availabilityExecutor;

    public AvailabilityService(SportyApiClient api, QueuedBookingRepository queuedBookings,
                               MyReservationsService myReservations, AppUserRepository users,
                               ClubService clubs, ClubRegistry registry, FixedBookingService fixedBookings,
                               ExecutorService availabilityExecutor) {
        this.api = api;
        this.queuedBookings = queuedBookings;
        this.myReservations = myReservations;
        this.users = users;
        this.clubs = clubs;
        this.registry = registry;
        this.fixedBookings = fixedBookings;
        this.availabilityExecutor = availabilityExecutor;
    }

    private record QueuedInfo(Long userId, String username) {}

    /**
     * Availability for the given clubs (defaults to all the user's clubs) and optionally a subset of
     * courts (empty = all courts of each club). Per-club fetches run concurrently.
     */
    public AvailabilityViews.Week getWeek(List<Long> clubIds, Set<Long> courtIds, LocalDate from, LocalDate to) {
        long currentUserId = UserContext.require();

        List<Long> targetClubs = (clubIds == null || clubIds.isEmpty())
                ? clubs.listForUser(currentUserId).stream().map(ClubView::clubId).toList()
                : clubIds.stream().filter(id -> clubs.hasAccess(currentUserId, id)).toList();

        Map<String, QueuedInfo> queued = queuedMap();
        Set<String> mineKeys = mineKeys(from, to);
        Map<String, QueuedInfo> fixedHeld = new HashMap<>();
        for (org.padel.booker.fixed.HeldSlot h : fixedBookings.heldSlots(from, to)) {
            fixedHeld.put(codeKey(h.courtId(), h.date().toString(), h.code()),
                    new QueuedInfo(h.userId(), h.username()));
        }

        // Fan out the slow per-club fetches in parallel.
        List<CompletableFuture<AvailabilityViews.ClubSection>> futures = new ArrayList<>();
        for (Long clubId : targetClubs) {
            futures.add(CompletableFuture.supplyAsync(
                    () -> fetchClubSection(currentUserId, clubId, courtIds, from, to, queued, mineKeys, fixedHeld),
                    availabilityExecutor));
        }

        List<AvailabilityViews.ClubSection> sections = new ArrayList<>();
        for (CompletableFuture<AvailabilityViews.ClubSection> f : futures) {
            sections.add(f.join());
        }
        // Keep a stable order matching the requested/registry order.
        sections.sort((a, b) -> Long.compare(order(a.clubId()), order(b.clubId())));
        return new AvailabilityViews.Week(from.toString(), to.toString(), sections);
    }

    private long order(long clubId) {
        List<org.padel.booker.club.SupportedClub> all = registry.all();
        for (int i = 0; i < all.size(); i++) if (all.get(i).clubId() == clubId) return i;
        return Long.MAX_VALUE;
    }

    private AvailabilityViews.ClubSection fetchClubSection(long userId, long clubId, Set<Long> courtIds,
                                                          LocalDate from, LocalDate to,
                                                          Map<String, QueuedInfo> queued, Set<String> mineKeys,
                                                          Map<String, QueuedInfo> fixedHeld) {
        String clubName = registry.find(clubId).map(org.padel.booker.club.SupportedClub::name).orElse("Club " + clubId);
        ClubRef ref = registry.ref(clubId);
        // Worker thread: re-establish the acting user so the API client can pick the right token.
        UserContext.set(userId);
        try {
            JsonNode resp = api.getCourtsWithAvailability(ref, from, to);
            JsonNode courts = resp.has("data") ? resp.get("data") : resp;

            List<AvailabilityViews.CourtWeek> courtWeeks = new ArrayList<>();
            for (JsonNode court : courts) {
                long courtId = court.path("id").asLong();
                if (courtIds != null && !courtIds.isEmpty() && !courtIds.contains(courtId)) continue;
                String name = court.path("name").asText();
                String type = court.path("type").asText();
                int duration = court.path("reservation_duration").asInt(90);
                JsonNode parsed = court.path("parsed_reservations");

                List<AvailabilityViews.CourtDay> days = new ArrayList<>();
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                    days.add(new AvailabilityViews.CourtDay(
                            d.toString(),
                            slotsForDay(courtId, d, parsed.path(d.toString()), queued, mineKeys, fixedHeld, userId)));
                }
                courtWeeks.add(new AvailabilityViews.CourtWeek(courtId, name, type, duration, days));
            }
            return new AvailabilityViews.ClubSection(clubId, clubName, false, courtWeeks);
        } catch (RuntimeException e) {
            log.warn("Availability fetch failed for club {}: {}", clubId, e.getMessage());
            return new AvailabilityViews.ClubSection(clubId, clubName, true, List.of());
        } finally {
            UserContext.clear();
        }
    }

    private List<AvailabilityViews.Slot> slotsForDay(long courtId, LocalDate date, JsonNode dayNode,
                                                     Map<String, QueuedInfo> queued, Set<String> mineKeys,
                                                     Map<String, QueuedInfo> fixedHeld, long currentUserId) {
        List<AvailabilityViews.Slot> slots = new ArrayList<>();
        if (dayNode == null || !dayNode.isObject()) return slots;

        TreeMap<Integer, JsonNode> byTime = new TreeMap<>();
        for (Map.Entry<String, JsonNode> e : dayNode.properties()) {
            JsonNode slot = e.getValue();
            int time = slot.path("time").asInt(parseIntOr(e.getKey(), -1));
            if (time < 0) continue;
            if (slot.path("hidden").asBoolean(false)) continue;
            byTime.put(time, slot);
        }

        for (Map.Entry<Integer, JsonNode> e : byTime.entrySet()) {
            int time = e.getKey();
            JsonNode slot = e.getValue();
            boolean booked = slot.path("id").asLong(0) != 0;
            String fmt = fmt(time);
            boolean mine = mineKeys.contains(key(courtId, date.toString(), fmt));

            String ck = codeKey(courtId, date.toString(), time);
            QueuedInfo qi = booked ? null : queued.get(ck);
            if (qi == null && !booked) qi = fixedHeld.get(ck); // standing weekly booking holds the slot
            boolean isQueued = qi != null;
            boolean queuedByMe = qi != null && qi.userId() != null && qi.userId() == currentUserId;
            String queuedBy = qi != null ? qi.username() : null;

            BigDecimal price = (!booked && slot.hasNonNull("price")) ? slot.get("price").decimalValue() : null;

            String bookedBy = null;
            List<AvailabilityViews.SlotPlayer> players = List.of();
            if (booked) {
                JsonNode by = slot.path("created_by");
                bookedBy = by.path("display_name").asText(by.path("name").asText(null));
                List<AvailabilityViews.SlotPlayer> ps = new ArrayList<>();
                for (JsonNode p : slot.path("players")) {
                    String pn = p.path("display_name").asText(null);
                    if (pn != null) ps.add(new AvailabilityViews.SlotPlayer(pn, p.path("image").asText(null)));
                }
                players = ps;
            }
            slots.add(new AvailabilityViews.Slot(time, fmt, booked, mine, isQueued, queuedByMe, queuedBy,
                    price, bookedBy, players));
        }
        return slots;
    }

    /** Map of PENDING queued slots -> who scheduled them (courtId|date|timeCode -> QueuedInfo). */
    private Map<String, QueuedInfo> queuedMap() {
        Map<Long, String> usernames = new HashMap<>();
        for (AppUser u : users.findAll()) usernames.put(u.getId(), u.getUsername());

        Map<String, QueuedInfo> map = new HashMap<>();
        for (QueuedBooking b : queuedBookings.findByStatusOrderByTargetFireAtAsc(BookingStatus.PENDING)) {
            String uname = b.getUserId() != null ? usernames.getOrDefault(b.getUserId(), "someone") : "someone";
            QueuedInfo info = new QueuedInfo(b.getUserId(), uname);
            for (int code : b.slotCodes()) {
                map.put(codeKey(b.getCourtId(), b.getPlayDate().toString(), code), info);
            }
        }
        return map;
    }

    /** Keys for the current player's own Sporty reservations in range: courtId|date|"HH:mm". Best-effort. */
    private Set<String> mineKeys(LocalDate from, LocalDate to) {
        Set<String> keys = new HashSet<>();
        try {
            for (MyReservationView r : myReservations.list(from)) {
                if (r.date() == null) continue;
                LocalDate d = LocalDate.parse(r.date());
                if (d.isBefore(from) || d.isAfter(to)) continue;
                keys.add(key(r.courtId(), r.date(), r.fromTime()));
            }
        } catch (RuntimeException e) {
            log.warn("Could not load own reservations for grid overlay: {}", e.getMessage());
        }
        return keys;
    }

    private static String fmt(int timeCode) {
        int hours = timeCode / 100;
        int minutes = Math.round((timeCode % 100) * 0.6f);
        return String.format("%02d:%02d", hours, minutes);
    }

    private static String key(long courtId, String date, String timeFmt) {
        return courtId + "|" + date + "|" + timeFmt;
    }

    private static String codeKey(long courtId, String date, int timeCode) {
        return courtId + "|" + date + "|c" + timeCode;
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
