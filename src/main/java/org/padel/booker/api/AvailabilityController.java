package org.padel.booker.api;

import tools.jackson.databind.JsonNode;
import org.padel.booker.availability.AvailabilityService;
import org.padel.booker.availability.AvailabilityViews;
import org.padel.booker.client.SportyApiClient;
import org.padel.booker.club.ClubRegistry;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api")
public class AvailabilityController {

    private final AvailabilityService availability;
    private final SportyApiClient api;
    private final ClubRegistry registry;

    public AvailabilityController(AvailabilityService availability, SportyApiClient api, ClubRegistry registry) {
        this.availability = availability;
        this.api = api;
        this.registry = registry;
    }

    /**
     * Live week grid for the given clubs (default: all the user's clubs) and optionally a subset of
     * courts. Defaults to the current week (today .. today+6) if no range given.
     */
    @GetMapping("/availability")
    public AvailabilityViews.Week week(
            @RequestParam(required = false) List<Long> clubs,
            @RequestParam(required = false) List<Long> courts,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now();
        LocalDate end = to != null ? to : start.plusDays(6);
        Set<Long> courtIds = courts != null ? new HashSet<>(courts) : Set.of();
        return availability.getWeek(clubs, courtIds, start, end);
    }

    /** Raw courts list for a club (live). */
    @GetMapping("/courts")
    public JsonNode courts(@RequestParam long club) {
        return api.getCourts(registry.ref(club));
    }
}
