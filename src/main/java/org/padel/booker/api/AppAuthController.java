package org.padel.booker.api;

import org.padel.booker.auth.AppTokenService;
import org.padel.booker.config.AppAuthProperties;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/app-login")
public class AppAuthController {

    private final AppAuthProperties props;
    private final AppTokenService tokens;

    public AppAuthController(AppAuthProperties props, AppTokenService tokens) {
        this.props = props;
        this.tokens = tokens;
    }

    public record LoginRequest(String username, String password) {}

    @PostMapping
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        if (props.getPassword() == null || props.getPassword().isBlank()) {
            throw new IllegalStateException("App login is not configured (set APP_PASSWORD)");
        }
        boolean userOk = constantTimeEquals(nullToEmpty(req.username()), props.getUsername());
        boolean passOk = constantTimeEquals(nullToEmpty(req.password()), props.getPassword());
        if (!userOk || !passOk) {
            throw new IllegalArgumentException("Invalid username or password");
        }
        long ttl = Duration.ofDays(props.getTokenTtlDays()).toMillis();
        String token = tokens.create(props.getUsername(), ttl);
        return Map.of(
                "token", token,
                "username", props.getUsername(),
                "expiresAt", Instant.now().plusMillis(ttl).toString()
        );
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
