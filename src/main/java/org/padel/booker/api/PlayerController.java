package org.padel.booker.api;

import org.padel.booker.players.PlayerView;
import org.padel.booker.players.ReservationPlayerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Search people within a club, to add them to a booking. */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final ReservationPlayerService players;

    public PlayerController(ReservationPlayerService players) {
        this.players = players;
    }

    @GetMapping("/search")
    public List<PlayerView> search(@RequestParam long club, @RequestParam(required = false) String q) {
        return players.search(club, q == null ? "" : q);
    }
}
