package org.padel.booker.weather;

import java.time.Instant;
import java.util.List;

/** A club's merged forecast, plus when it was fetched from the upstream sources. */
public record ClubForecast(long clubId, Instant fetchedAt, List<WeatherBlock> blocks) {}
