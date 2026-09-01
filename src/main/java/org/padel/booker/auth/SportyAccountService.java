package org.padel.booker.auth;

import org.padel.booker.domain.Credential;
import org.padel.booker.repo.CredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns each app user's Sporty Plus bearer token, keyed by app-user id. Serves the current token and
 * can mint a fresh one from that user's stored email/password when it's missing or rejected.
 */
@Service
public class SportyAccountService {

    private static final Logger log = LoggerFactory.getLogger(SportyAccountService.class);

    private final RestClient authClient;
    private final CredentialRepository credentials;
    private final ConcurrentHashMap<Long, String> tokenCache = new ConcurrentHashMap<>();

    public SportyAccountService(@Qualifier("authRestClient") RestClient authClient,
                                CredentialRepository credentials) {
        this.authClient = authClient;
        this.credentials = credentials;
    }

    public boolean isConfigured(long userId) {
        return credentials.findByUserId(userId)
                .map(c -> c.getEmail() != null && !c.getEmail().isBlank())
                .orElse(false);
    }

    public String getToken(long userId) {
        String cached = tokenCache.get(userId);
        if (cached != null) return cached;
        Credential c = requireCredential(userId);
        if (c.getToken() != null && !c.getToken().isBlank()) {
            tokenCache.put(userId, c.getToken());
            return c.getToken();
        }
        return login(userId);
    }

    public synchronized String refreshToken(long userId) {
        log.info("Refreshing Sporty token for user {}", userId);
        return login(userId);
    }

    public void evict(long userId) {
        tokenCache.remove(userId);
    }

    private synchronized String login(long userId) {
        Credential c = requireCredential(userId);
        if (c.getEmail() == null || c.getPassword() == null) {
            throw new IllegalStateException("Cannot login: Sporty email/password not set");
        }
        LoginResponse resp = authClient.post()
                .uri("/login")
                .body(Map.of("client", "native", "email", c.getEmail(), "password", c.getPassword()))
                .retrieve()
                .body(LoginResponse.class);
        if (resp == null || resp.token() == null) {
            throw new IllegalStateException("Login returned no token");
        }
        c.setToken(resp.token());
        c.setTokenExpiresAt(parseExpiry(resp));
        c.setLastValidatedAt(Instant.now());
        credentials.save(c);
        tokenCache.put(userId, resp.token());
        log.info("Obtained Sporty token for user {} (expires {})", userId, c.getTokenExpiresAt());
        return resp.token();
    }

    private Instant parseExpiry(LoginResponse resp) {
        try {
            if (resp.expiresAt() != null) return OffsetDateTime.parse(resp.expiresAt()).toInstant();
        } catch (RuntimeException ignored) {
        }
        if (resp.expiresIn() != null) return Instant.now().plusSeconds(resp.expiresIn());
        return null;
    }

    private Credential requireCredential(long userId) {
        return credentials.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("No Sporty account linked. Set it on the Settings page."));
    }
}
