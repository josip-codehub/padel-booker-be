package org.padel.booker.api;

import org.padel.booker.auth.UserContext;
import org.padel.booker.fixed.FixedBookingService;
import org.padel.booker.fixed.FixedBookingView;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin management of standing weekly ("fixed") bookings. Creation happens via the normal booking
 * endpoint with {@code repeatWeekly=true}; this is for listing and cancelling. Admin-only is enforced
 * by the auth filter on /api/admin/**.
 */
@RestController
@RequestMapping("/api/admin/fixed-bookings")
public class AdminFixedBookingController {

    private final FixedBookingService fixedBookings;

    public AdminFixedBookingController(FixedBookingService fixedBookings) {
        this.fixedBookings = fixedBookings;
    }

    @GetMapping
    public List<FixedBookingView> list() {
        return fixedBookings.listForUser(UserContext.require());
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> cancel(@PathVariable long id) {
        fixedBookings.cancel(UserContext.require(), id);
        return Map.of("cancelled", true, "id", id);
    }
}
