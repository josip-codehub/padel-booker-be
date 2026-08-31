package org.padel.booker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Two RestClients: one for the auth host (login) and one for the data host (everything else).
 * Common browser-like headers the upstream expects are baked in.
 */
@Configuration
public class RestClientConfig {

    private static SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(Duration.ofSeconds(5));
        // The upstream reservation list is slow (heavy embedded payload, ~7-25s for a club week),
        // so give reads plenty of headroom. Note: the midnight burst uses createReservation, whose
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
        return RestClient.builder()
                .baseUrl(props.getApiBaseUrl())
                .requestFactory(requestFactory())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json, text/plain, */*")
                .defaultHeader("origin", props.getOrigin())
                .defaultHeader("x-language", props.getLanguage())
                .build();
    }
}
