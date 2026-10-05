package org.padel.booker.weather;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.padel.booker.club.ClubRegistry;
import org.padel.booker.club.SupportedClub;
import org.padel.booker.config.SportyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forecast for each club's location, merged from two free sources:
 * - MET Norway Locationforecast 2.0 (the data behind yr.no): hourly for ~2.5 days, then 6-hour blocks,
 *   up to ~9.5 days.
 * - Open-Meteo: fills the days after MET's horizon (Silax books 14 days ahead), aggregated into the same
 *   6-hour UTC-aligned blocks so far days look consistent and don't pretend to be hourly-accurate.
 *
 * Cached per club for the whole local day: the first request of a new day fetches, later requests serve the
 * cache. A forced refresh refetches (MET via If-Modified-Since) but at most once per {@link #REFRESH_THROTTLE},
 * to respect MET's terms of service.
 */
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final Duration REFRESH_THROTTLE = Duration.ofMinutes(10);
    /** MET requires an identifying User-Agent (application + contact). */
    private static final String USER_AGENT = "padel-booker/1.0 (+https://padel-booker.codehub.hr)";
    private static final DateTimeFormatter LOCAL_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final DateTimeFormatter OM_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private final ClubRegistry registry;
    private final ObjectMapper mapper;
    private final ZoneId zone;
    private final RestClient http;
    private final ConcurrentHashMap<Long, Cached> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

    private record Cached(LocalDate day, Instant fetchedAt, String metLastModified,
                          List<WeatherBlock> met, List<WeatherBlock> openMeteo) {}

    private record MetResult(List<WeatherBlock> blocks, String lastModified) {}

    public WeatherService(ClubRegistry registry, ObjectMapper mapper, SportyProperties props) {
        this.registry = registry;
        this.mapper = mapper;
        this.zone = ZoneId.of(props.getTimezone());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(20));
        this.http = RestClient.builder()
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT)
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    /** Forecasts for the given supported clubs; {@code force} = user pressed "Refresh forecast". */
    public List<ClubForecast> forecasts(List<Long> clubIds, boolean force) {
        List<ClubForecast> out = new ArrayList<>();
        for (Long clubId : clubIds) {
            registry.find(clubId).ifPresent(club -> out.add(forecast(club, force)));
        }
        return out;
    }

    private ClubForecast forecast(SupportedClub club, boolean force) {
        long clubId = club.clubId();
        synchronized (locks.computeIfAbsent(clubId, id -> new Object())) {
            Cached c = cache.get(clubId);
            LocalDate today = LocalDate.now(zone);
            boolean newDay = c == null || !c.day().equals(today);
            boolean refreshAllowed = c == null
                    || Duration.between(c.fetchedAt(), Instant.now()).compareTo(REFRESH_THROTTLE) >= 0;
            if (newDay || (force && refreshAllowed)) {
                c = fetch(club, c, today);
                cache.put(clubId, c);
            }
            return new ClubForecast(clubId, c.fetchedAt(), merge(c));
        }
    }

    private Cached fetch(SupportedClub club, Cached prev, LocalDate today) {
        List<WeatherBlock> met = prev != null ? prev.met() : List.of();
        String lastModified = prev != null && !prev.met().isEmpty() ? prev.metLastModified() : null;
        try {
            MetResult r = fetchMet(club, lastModified);
            if (r != null) { // null = 304 Not Modified -> keep what we have
                met = r.blocks();
                lastModified = r.lastModified();
            }
        } catch (RuntimeException e) {
            log.warn("MET forecast fetch failed for club {}: {}", club.clubId(), e.getMessage());
        }
        List<WeatherBlock> openMeteo = prev != null ? prev.openMeteo() : List.of();
        try {
            openMeteo = fetchOpenMeteo(club);
        } catch (RuntimeException e) {
            log.warn("Open-Meteo forecast fetch failed for club {}: {}", club.clubId(), e.getMessage());
        }
        log.info("Fetched forecast for club {}: {} MET + {} Open-Meteo blocks", club.clubId(), met.size(), openMeteo.size());
        return new Cached(today, Instant.now(), lastModified, met, openMeteo);
    }

    /** MET blocks first; Open-Meteo only for the time after MET's last block. */
    private List<WeatherBlock> merge(Cached c) {
        List<WeatherBlock> out = new ArrayList<>(c.met());
        String metEnd = null;
        if (!c.met().isEmpty()) {
            WeatherBlock last = c.met().get(c.met().size() - 1);
            metEnd = LocalDateTime.parse(last.start(), LOCAL_FMT).plusHours(last.hours()).format(LOCAL_FMT);
        }
        for (WeatherBlock b : c.openMeteo()) {
            if (metEnd == null || b.start().compareTo(metEnd) >= 0) out.add(b);
        }
        out.sort(Comparator.comparing(WeatherBlock::start));
        return out;
    }

    // ---- MET Norway --------------------------------------------------------

    private MetResult fetchMet(SupportedClub club, String ifModifiedSince) {
        String lat = coord(club.latitude());
        String lon = coord(club.longitude());
        return http.get()
                .uri("https://api.met.no/weatherapi/locationforecast/2.0/compact?lat={lat}&lon={lon}", lat, lon)
                .headers(h -> {
                    if (ifModifiedSince != null) h.set(HttpHeaders.IF_MODIFIED_SINCE, ifModifiedSince);
                })
                .exchange((req, res) -> {
                    int status = res.getStatusCode().value();
                    if (status == 304) return null;
                    if (status != 200) throw new IllegalStateException("MET HTTP " + status);
                    JsonNode body = mapper.readTree(res.getBody());
                    return new MetResult(parseMet(body), res.getHeaders().getFirst(HttpHeaders.LAST_MODIFIED));
                });
    }

    private List<WeatherBlock> parseMet(JsonNode body) {
        // Instant temperatures by time, so a 6-hour block can show the average of its start and end.
        Map<Instant, Double> temps = new HashMap<>();
        for (JsonNode ts : body.path("properties").path("timeseries")) {
            JsonNode t = ts.path("data").path("instant").path("details").path("air_temperature");
            if (t.isNumber()) temps.put(Instant.parse(ts.path("time").asText()), t.asDouble());
        }
        List<WeatherBlock> out = new ArrayList<>();
        for (JsonNode ts : body.path("properties").path("timeseries")) {
            Instant time = Instant.parse(ts.path("time").asText());
            JsonNode data = ts.path("data");
            int hours;
            String symbol;
            if (data.has("next_1_hours")) {
                hours = 1;
                symbol = data.path("next_1_hours").path("summary").path("symbol_code").asText(null);
            } else if (data.has("next_6_hours")) {
                hours = 6;
                symbol = data.path("next_6_hours").path("summary").path("symbol_code").asText(null);
            } else {
                continue;
            }
            if (symbol == null) continue;
            Double start = temps.get(time);
            Double end = temps.get(time.plus(Duration.ofHours(hours)));
            Double temp = (hours > 1 && start != null && end != null) ? (start + end) / 2 : start;
            out.add(new WeatherBlock(local(time), hours, round1(temp), symbol, "met"));
        }
        return out;
    }

    // ---- Open-Meteo --------------------------------------------------------

    private List<WeatherBlock> fetchOpenMeteo(SupportedClub club) {
        JsonNode body = http.get()
                .uri("https://api.open-meteo.com/v1/forecast?latitude={lat}&longitude={lon}"
                                + "&hourly=temperature_2m,weather_code&forecast_days=16&timezone=GMT",
                        coord(club.latitude()), coord(club.longitude()))
                .retrieve()
                .body(JsonNode.class);
        if (body == null) return List.of();
        JsonNode times = body.path("hourly").path("time");
        JsonNode temps = body.path("hourly").path("temperature_2m");
        JsonNode codes = body.path("hourly").path("weather_code");

        // Aggregate hourly values into 6-hour blocks aligned to UTC 00/06/12/18 (same as MET's far blocks).
        TreeMap<Instant, List<Integer>> bucketIdx = new TreeMap<>();
        for (int i = 0; i < times.size(); i++) {
            Instant t = LocalDateTime.parse(times.get(i).asText(), OM_FMT).toInstant(ZoneOffset.UTC);
            long hourOfDay = t.atOffset(ZoneOffset.UTC).getHour();
            Instant bucket = t.minus(Duration.ofHours(hourOfDay % 6));
            bucketIdx.computeIfAbsent(bucket, k -> new ArrayList<>()).add(i);
        }
        List<WeatherBlock> out = new ArrayList<>();
        for (Map.Entry<Instant, List<Integer>> e : bucketIdx.entrySet()) {
            List<Integer> idx = e.getValue();
            if (idx.size() < 6) continue; // partial bucket at the edges
            double sum = 0;
            int n = 0;
            int maxCode = -1;
            for (int i : idx) {
                if (temps.get(i).isNumber()) { sum += temps.get(i).asDouble(); n++; }
                if (codes.get(i).isNumber()) maxCode = Math.max(maxCode, codes.get(i).asInt());
            }
            if (n == 0 || maxCode < 0) continue;
            int midLocalHour = e.getKey().plus(Duration.ofHours(3)).atZone(zone).getHour();
            boolean day = midLocalHour >= 7 && midLocalHour < 19;
            out.add(new WeatherBlock(local(e.getKey()), 6, round1(sum / n), wmoToMet(maxCode, day), "open-meteo"));
        }
        return out;
    }

    /** WMO weather code (Open-Meteo) -> MET symbol code (bundled icon file name). Higher code = more severe. */
    static String wmoToMet(int code, boolean day) {
        String dn = day ? "_day" : "_night";
        return switch (code) {
            case 0 -> "clearsky" + dn;
            case 1 -> "fair" + dn;
            case 2 -> "partlycloudy" + dn;
            case 3 -> "cloudy";
            case 45, 48 -> "fog";
            case 51, 53, 56, 61 -> "lightrain";
            case 55, 57, 63 -> "rain";
            case 65 -> "heavyrain";
            case 66 -> "lightsleet";
            case 67 -> "sleet";
            case 71, 77 -> "lightsnow";
            case 73 -> "snow";
            case 75 -> "heavysnow";
            case 80 -> "lightrainshowers" + dn;
            case 81 -> "rainshowers" + dn;
            case 82 -> "heavyrainshowers" + dn;
            case 85 -> "lightsnowshowers" + dn;
            case 86 -> "heavysnowshowers" + dn;
            case 95 -> "rainandthunder";
            case 96, 99 -> "heavyrainandthunder";
            default -> "cloudy";
        };
    }

    // ---- helpers -----------------------------------------------------------

    private String local(Instant t) {
        return LocalDateTime.ofInstant(t, zone).format(LOCAL_FMT);
    }

    /** MET rejects more than 4 decimals. */
    private static String coord(double v) {
        return String.format(Locale.ROOT, "%.4f", v);
    }

    private static Double round1(Double v) {
        return v == null ? null : Math.round(v * 10) / 10.0;
    }
}
