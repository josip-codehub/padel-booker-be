package org.padel.booker.api;

import org.padel.booker.mine.MyReservationView;
import org.padel.booker.mine.MyReservationsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/my-reservations")
public class MyReservationsController {

    private final MyReservationsService service;

    public MyReservationsController(MyReservationsService service) {
        this.service = service;
    }

    /** The logged-in player's upcoming reservations (live from Sporty Plus). */
    @GetMapping
    public List<MyReservationView> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from) {
        return service.list(from);
    }

    /** Cancel one of the player's reservations on Sporty Plus. */
    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(@PathVariable long id) {
        service.cancel(id);
        return Map.of("canceled", true, "id", id);
    }
}
