package org.padel.booker.weather;

/**
 * One forecast block in club-local time: {@code hours} long starting at {@code start}
 * ("yyyy-MM-ddTHH:mm"). {@code symbol} is a MET symbol code (matches the bundled icon file names).
 */
public record WeatherBlock(String start, int hours, Double tempC, String symbol, String source) {}
