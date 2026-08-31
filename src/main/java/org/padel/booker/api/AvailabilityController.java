package org.padel.booker.api;

import tools.jackson.databind.JsonNode;
import org.padel.booker.availability.AvailabilityService;
import org.padel.booker.availability.AvailabilityViews;
import org.padel.booker.client.SportyApiClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class AvailabilityController {

    private final AvailabilityService availability;
    private final SportyApiClient api;

    public AvailabilityController(AvailabilityService availability, SportyApiClient api) {
        this.availability = availability;
        this.api = api;
    }

    /** Live week grid. Defaults to the current week (today .. today+6) if no range given. */
    @GetMapping("/availability")
    public AvailabilityViews.Week week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(6);
        return availability.getWeek(start, end);
    }

    /** Raw courts list (live). */
    @GetMapping("/courts")
    public JsonNode courts() {
        return api.getCourts();
    }
}
