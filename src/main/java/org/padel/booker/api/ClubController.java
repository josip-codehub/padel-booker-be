package org.padel.booker.api;

import org.padel.booker.auth.UserContext;
import org.padel.booker.club.ClubService;
import org.padel.booker.club.ClubView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** The current user's supported clubs (drives the club switcher and booking rules). */
@RestController
@RequestMapping("/api/clubs")
public class ClubController {

    private final ClubService clubs;

    public ClubController(ClubService clubs) {
        this.clubs = clubs;
    }

    @GetMapping
    public List<ClubView> list() {
        return clubs.listForUser(UserContext.require());
    }

    /** Re-discover the user's clubs from Sporty (e.g. after joining a new club). */
    @PostMapping("/refresh")
    public List<ClubView> refresh() {
        long userId = UserContext.require();
        clubs.refreshForUser(userId);
        return clubs.listForUser(userId);
    }
}
