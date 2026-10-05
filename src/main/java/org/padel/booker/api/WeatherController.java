package org.padel.booker.api;

import org.padel.booker.weather.ClubForecast;
import org.padel.booker.weather.WeatherService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Forecast per club for the availability grid (cached for the day; refresh is throttled). */
@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private final WeatherService weather;

    public WeatherController(WeatherService weather) {
        this.weather = weather;
    }

    @GetMapping
    public List<ClubForecast> get(@RequestParam List<Long> clubs) {
        return weather.forecasts(clubs, false);
    }

    @PostMapping("/refresh")
    public List<ClubForecast> refresh(@RequestParam List<Long> clubs) {
        return weather.forecasts(clubs, true);
    }
}
