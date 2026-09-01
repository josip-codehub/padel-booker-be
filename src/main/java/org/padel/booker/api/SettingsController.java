package org.padel.booker.api;

import org.padel.booker.auth.SportyAccountService;
import org.padel.booker.auth.UserContext;
import org.padel.booker.domain.Credential;
import org.padel.booker.repo.CredentialRepository;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/** Manages the CURRENT user's Sporty Plus account (email/password/token). */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final CredentialRepository credentials;
    private final SportyAccountService accounts;

    public SettingsController(CredentialRepository credentials, SportyAccountService accounts) {
        this.credentials = credentials;
        this.accounts = accounts;
    }

    public record SettingsRequest(String email, String password, String token) {}

    public record SettingsView(boolean configured, String email, Instant tokenExpiresAt, Instant lastValidatedAt) {}

    @GetMapping
    public SettingsView get() {
        long userId = UserContext.require();
        return credentials.findByUserId(userId)
                .map(c -> new SettingsView(c.getEmail() != null && !c.getEmail().isBlank(),
                        c.getEmail(), c.getTokenExpiresAt(), c.getLastValidatedAt()))
                .orElse(new SettingsView(false, null, null, null));
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody SettingsRequest req) {
        long userId = UserContext.require();
        Credential c = credentials.findByUserId(userId).orElseGet(() -> {
            Credential fresh = new Credential();
            fresh.setUserId(userId);
            return fresh;
        });
        if (req.email() != null) c.setEmail(req.email());
        if (req.password() != null) c.setPassword(req.password());
        if (req.token() != null && !req.token().isBlank()) c.setToken(req.token());
        credentials.save(c);
        accounts.evict(userId); // drop any cached token so the new creds take effect

        boolean loginOk;
        String message;
        try {
            if (c.getPassword() != null && !c.getPassword().isBlank()) {
                accounts.refreshToken(userId);
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
