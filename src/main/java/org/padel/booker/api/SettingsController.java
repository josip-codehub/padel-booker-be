package org.padel.booker.api;

import org.padel.booker.auth.AuthService;
import org.padel.booker.domain.Credential;
import org.padel.booker.repo.CredentialRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final CredentialRepository credentials;
    private final AuthService auth;

    public SettingsController(CredentialRepository credentials, AuthService auth) {
        this.credentials = credentials;
        this.auth = auth;
    }

    public record SettingsRequest(String email, String password, String token) {}

    public record SettingsView(boolean configured, String email, Instant tokenExpiresAt, Instant lastValidatedAt) {}

    @GetMapping
    public SettingsView get() {
        return credentials.findFirstByOrderByIdAsc()
                .map(c -> new SettingsView(true, c.getEmail(), c.getTokenExpiresAt(), c.getLastValidatedAt()))
                .orElse(new SettingsView(false, null, null, null));
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody SettingsRequest req) {
        Credential c = credentials.findFirstByOrderByIdAsc().orElseGet(Credential::new);
        if (req.email() != null) c.setEmail(req.email());
        if (req.password() != null) c.setPassword(req.password());
        if (req.token() != null && !req.token().isBlank()) c.setToken(req.token());
        credentials.save(c);

        // If we have a password, mint a fresh token now so we can confirm the login works.
        boolean loginOk;
        String message;
        try {
            if (c.getPassword() != null && !c.getPassword().isBlank()) {
                auth.refreshToken();
                loginOk = true;
                message = "Login succeeded; token stored.";
            } else {
                loginOk = c.getToken() != null;
                message = loginOk ? "Token stored (no password given to verify login)." : "Saved, but no token or password set.";
            }
        } catch (RuntimeException e) {
            loginOk = false;
            message = "Saved, but login failed: " + e.getMessage();
        }
        return Map.of("configured", true, "loginOk", loginOk, "message", message);
    }
}
