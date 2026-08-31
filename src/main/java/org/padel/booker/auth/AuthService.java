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

/**
 * Owns the Sporty Plus bearer token. Serves the current token, and can mint a fresh one
 * from the stored email/password when the token is missing or rejected.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final RestClient authClient;
    private final CredentialRepository credentials;

    private volatile String cachedToken;

    public AuthService(@Qualifier("authRestClient") RestClient authClient,
                       CredentialRepository credentials) {
        this.authClient = authClient;
        this.credentials = credentials;
    }

    /** Whether a credential row exists at all (used by health/settings). */
    public boolean isConfigured() {
        return credentials.findFirstByOrderByIdAsc().isPresent();
    }

    /** The current token, logging in if we don't have one yet. */
    public String getToken() {
        String t = cachedToken;
        if (t != null) return t;
        Credential c = requireCredential();
        if (c.getToken() != null && !c.getToken().isBlank()) {
            cachedToken = c.getToken();
            return cachedToken;
        }
        return login();
    }

    /** Force a fresh login (used on 401). */
    public synchronized String refreshToken() {
        log.info("Refreshing Sporty Plus token via login");
        return login();
    }

    private synchronized String login() {
        Credential c = requireCredential();
        if (c.getEmail() == null || c.getPassword() == null) {
            throw new IllegalStateException("Cannot login: email/password not set");
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
        cachedToken = resp.token();
        log.info("Obtained new token, expires at {}", c.getTokenExpiresAt());
        return cachedToken;
    }

    private Instant parseExpiry(LoginResponse resp) {
        try {
            if (resp.expiresAt() != null) {
                return OffsetDateTime.parse(resp.expiresAt()).toInstant();
            }
        } catch (RuntimeException ignored) {
            // fall through to expires_in
        }
        if (resp.expiresIn() != null) {
            return Instant.now().plusSeconds(resp.expiresIn());
        }
        return null;
    }

    private Credential requireCredential() {
        return credentials.findFirstByOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("No credentials configured. Set them on the Settings page."));
    }
}
