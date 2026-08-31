package org.padel.booker.api;

import jakarta.validation.Valid;
import org.padel.booker.booking.BookingService;
import org.padel.booker.booking.CreateBookingRequest;
import org.padel.booker.booking.ReserveNowResult;
import org.padel.booker.domain.BookingAttempt;
import org.padel.booker.domain.QueuedBooking;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QueuedBooking create(@Valid @RequestBody CreateBookingRequest req) {
        return service.queue(req);
    }

    /** Book immediately (for dates already inside the 1-week window). */
    @PostMapping("/now")
    public ReserveNowResult reserveNow(@Valid @RequestBody CreateBookingRequest req) {
        return service.reserveNow(req);
    }

    @GetMapping
    public List<QueuedBooking> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public QueuedBooking get(@PathVariable long id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    public QueuedBooking cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    @GetMapping("/{id}/attempts")
    public List<BookingAttempt> attempts(@PathVariable long id) {
        return service.attemptsFor(id);
    }
}
