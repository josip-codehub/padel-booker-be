package org.padel.booker.api;

import org.padel.booker.mine.MyReservationView;
import org.padel.booker.mine.MyReservationsService;
import org.padel.booker.players.ReservationPlayer;
import org.padel.booker.players.ReservationPlayerService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/my-reservations")
public class MyReservationsController {

    private final MyReservationsService service;
    private final ReservationPlayerService players;

    public MyReservationsController(MyReservationsService service, ReservationPlayerService players) {
        this.service = service;
        this.players = players;
    }

    /** The logged-in player's upcoming reservations (live from Sporty Plus). */
    @GetMapping
    public List<MyReservationView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return service.list(from);
    }

    /** Cancel one of the player's reservations on Sporty Plus. */
    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(@PathVariable long id, @RequestParam(required = false) Long club) {
        service.cancel(club != null ? club : 0L, id);
        return Map.of("canceled", true, "id", id);
    }

    // --- players on an existing reservation ---

    public record AddPlayersRequest(Long club, Long court, LocalDate date, List<Long> playerIds) {}

    @GetMapping("/{id}/players")
    public List<ReservationPlayer> getPlayers(
            @PathVariable long id,
            @RequestParam long club,
            @RequestParam long court,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return players.getPlayers(club, id, court, date);
    }

    @PostMapping("/{id}/players")
    public List<ReservationPlayer> addPlayers(@PathVariable long id, @RequestBody AddPlayersRequest req) {
        return players.addPlayers(req.club(), id, req.court(), req.date(), req.playerIds());
    }

    @DeleteMapping("/{id}/players/{playerId}")
    public List<ReservationPlayer> removePlayer(
            @PathVariable long id,
            @PathVariable long playerId,
            @RequestParam long club,
            @RequestParam long court,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return players.removePlayer(club, id, court, date, playerId);
    }
}
