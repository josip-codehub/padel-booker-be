package org.padel.booker.club;

import org.padel.booker.client.ClubRef;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.domain.UserClub;
import org.padel.booker.repo.UserClubRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Discovers which supported clubs a user belongs to and their per-user booking window, and resolves a
 * club id to the {@link ClubRef} (origin) the API client needs. Membership comes from
 * {@code GET /v2/me -> user.club_member}; the advance window comes from {@code /me} fetched with that
 * club's origin (it's a per-membership value), cached in {@code user_clubs}.
 */
@Service
public class ClubService {

    private static final Logger log = LoggerFactory.getLogger(ClubService.class);

    private final SportyApiClient api;
    private final ClubRegistry registry;
    private final UserClubRepository userClubs;

    public ClubService(SportyApiClient api, ClubRegistry registry, UserClubRepository userClubs) {
        this.api = api;
        this.registry = registry;
        this.userClubs = userClubs;
    }

    /** ClubRef (origin) for a supported club, or an error if unsupported. */
    public ClubRef ref(long clubId) {
        return registry.ref(clubId);
    }

    /** The advance-booking window for this user at this club (cached; registry fallback). */
    public int spanDays(long userId, long clubId) {
        return userClubs.findByUserIdAndClubId(userId, clubId)
                .filter(UserClub::isEnabled)
                .map(UserClub::getMaxSpanDays)
                .orElseGet(() -> registry.require(clubId).fallbackSpanDays());
    }

    /** True if the user is a member of this supported club. */
    public boolean hasAccess(long userId, long clubId) {
        return registry.isSupported(clubId)
                && userClubs.findByUserIdAndClubId(userId, clubId).map(UserClub::isEnabled).orElse(false);
    }

    public void requireAccess(long userId, long clubId) {
        if (!registry.isSupported(clubId)) {
            throw new IllegalArgumentException("Club " + clubId + " is not supported");
        }
        if (!hasAccess(userId, clubId)) {
            throw new IllegalArgumentException("You are not a member of club " + clubId);
        }
    }

    /** The user's supported clubs for the UI; lazily refreshes from Sporty if we have none cached. */
    public List<ClubView> listForUser(long userId) {
        List<UserClub> rows = userClubs.findByUserIdAndEnabledTrueOrderByClubId(userId);
        if (rows.isEmpty()) {
            try {
                refreshForUser(userId);
                rows = userClubs.findByUserIdAndEnabledTrueOrderByClubId(userId);
            } catch (RuntimeException e) {
                log.info("Could not auto-discover clubs for user {}: {}", userId, e.getMessage());
            }
        }
        List<ClubView> out = new ArrayList<>();
        for (UserClub uc : rows) {
            registry.find(uc.getClubId()).ifPresent(c ->
                    out.add(new ClubView(c.clubId(), c.name(), c.defaultSlotMinutes(),
                            c.minSlots(), c.maxSlots(), uc.getMaxSpanDays())));
        }
        return out;
    }

    /**
     * Re-read the user's club memberships from Sporty and upsert {@code user_clubs}: enable supported
     * clubs they belong to (capturing each club's span), disable ones they no longer have. Requires a
     * linked Sporty account.
     */
    @Transactional
    public void refreshForUser(long userId) {
        JsonNode me = api.me(); // club_member is returned regardless of origin
        JsonNode clubMember = me.path("user").path("club_member");

        Set<Long> membershipClubIds = new HashSet<>();
        if (clubMember.isObject()) {
            for (Map.Entry<String, JsonNode> e : clubMember.properties()) {
                parseLong(e.getKey()).filter(registry::isSupported).ifPresent(membershipClubIds::add);
            }
        }

        Map<Long, UserClub> existing = new java.util.HashMap<>();
        for (UserClub uc : userClubs.findByUserId(userId)) existing.put(uc.getClubId(), uc);

        for (Long clubId : membershipClubIds) {
            SupportedClub cfg = registry.require(clubId);
            int span = fetchSpanDays(cfg);
            UserClub uc = existing.getOrDefault(clubId, new UserClub());
            uc.setUserId(userId);
            uc.setClubId(clubId);
            uc.setMaxSpanDays(span);
            uc.setEnabled(true);
            userClubs.save(uc);
        }
        // Disable supported clubs the user no longer belongs to.
        for (UserClub uc : existing.values()) {
            if (!membershipClubIds.contains(uc.getClubId()) && uc.isEnabled()) {
                uc.setEnabled(false);
                userClubs.save(uc);
            }
        }
        log.info("Refreshed clubs for user {}: {} supported membership(s)", userId, membershipClubIds.size());
    }

    private int fetchSpanDays(SupportedClub cfg) {
        try {
            JsonNode me = api.me(cfg.origin());
            int span = me.path("user").path("membership").path("max_reservation_span").asInt(0);
            return span > 0 ? span : cfg.fallbackSpanDays();
        } catch (RuntimeException e) {
            log.warn("Could not read span for club {} ({}); using fallback {}", cfg.clubId(),
                    e.getMessage(), cfg.fallbackSpanDays());
            return cfg.fallbackSpanDays();
        }
    }

    private static Optional<Long> parseLong(String s) {
        try {
            return Optional.of(Long.parseLong(s.trim()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
