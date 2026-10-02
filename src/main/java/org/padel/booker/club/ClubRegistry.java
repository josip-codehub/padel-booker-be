package org.padel.booker.club;

import org.padel.booker.client.ClubRef;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The set of clubs the app supports. Only Modesto and Silax are verified for now; other clubs a user
 * belongs to stay hidden until their rules are confirmed. Adding a club later = one entry here.
 */
@Component
public class ClubRegistry {

    private final List<SupportedClub> clubs = List.of(
            // Modesto: a single 90-minute padel slot per booking, 7-day window.
            new SupportedClub(139, "Modesto", "https://modesto.sporty.plus", 90, 1, 1, 7),
            // Silax Sisak: 30-minute slots, 2..4 of them per booking (1h min, 2h max), 14-day window.
            new SupportedClub(20, "Silax Sisak", "https://silax.sporty.plus", 30, 2, 4, 14)
    );

    public List<SupportedClub> all() {
        return clubs;
    }

    public Optional<SupportedClub> find(long clubId) {
        return clubs.stream().filter(c -> c.clubId() == clubId).findFirst();
    }

    public boolean isSupported(long clubId) {
        return find(clubId).isPresent();
    }

    /** The club config, or an error if the club isn't supported. */
    public SupportedClub require(long clubId) {
        return find(clubId).orElseThrow(() ->
                new IllegalArgumentException("Club " + clubId + " is not supported"));
    }

    public ClubRef ref(long clubId) {
        return require(clubId).ref();
    }
}
