package org.padel.booker.api;

import org.padel.booker.booking.BookingService;
import org.padel.booker.domain.BookingAttempt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attempts")
public class AttemptController {

    private final BookingService service;

    public AttemptController(BookingService service) {
        this.service = service;
    }

    /** Recent attempts across all bookings (history page). */
    @GetMapping
    public List<BookingAttempt> recent() {
        return service.recentAttempts();
    }
}
