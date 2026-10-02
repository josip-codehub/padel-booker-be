package org.padel.booker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Two RestClients: one for the auth host (login) and one for the data host (everything else).
 * Common browser-like headers the upstream expects are baked in.
 *
 * Uses the JDK {@link HttpClient} factory (not {@code SimpleClientHttpRequestFactory}/HttpURLConnection)
 * because HttpURLConnection silently strips the restricted {@code Origin} header — and we rely on Origin
 * to select the club context (e.g. per-club max_reservation_span from /me).
 */
@Configuration
public class RestClientConfig {

    private static ClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(httpClient);
        // The upstream reservation list is slow (heavy embedded payload, ~7-25s for a club week),
        // so give reads plenty of headroom. The midnight burst uses createReservation, whose
        // responses are tiny and fast; this generous read timeout does not slow the burst.
        f.setReadTimeout(Duration.ofSeconds(60));
        return f;
    }

    @Bean("authRestClient")
    public RestClient authRestClient(SportyProperties props) {
        return RestClient.builder()
                .baseUrl(props.getAuthBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .defaultHeader("origin", props.getOrigin())
                .defaultHeader("x-language", props.getLanguage())
                .build();
    }

    @Bean("apiRestClient")
    public RestClient apiRestClient(SportyProperties props) {
        // No default origin here: the origin selects the club context (and drives per-club values like
        // max_reservation_span), so every data call sets it explicitly. A leftover default header would
        // shadow that per-request origin.
        return RestClient.builder()
                .baseUrl(props.getApiBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .defaultHeader("x-language", props.getLanguage())
                .build();
    }
}
