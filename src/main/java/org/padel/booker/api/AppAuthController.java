package org.padel.booker.api;

import org.padel.booker.auth.AppTokenService;
import org.padel.booker.auth.AppUserService;
import org.padel.booker.config.AppAuthProperties;
import org.padel.booker.domain.AppUser;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api/app-login")
public class AppAuthController {

    private final AppUserService userService;
    private final AppTokenService tokens;
    private final AppAuthProperties props;

    public AppAuthController(AppUserService userService, AppTokenService tokens, AppAuthProperties props) {
        this.userService = userService;
        this.tokens = tokens;
        this.props = props;
    }

    public record LoginRequest(String username, String password) {}

    @PostMapping
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        AppUser user = userService.authenticate(req.username(), req.password())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username or password"));
        long ttl = Duration.ofDays(props.getTokenTtlDays()).toMillis();
        String token = tokens.create(user.getId(), user.getRole(), ttl);
        return Map.of(
                "token", token,
                "username", user.getUsername(),
                "role", user.getRole().name(),
                "mustChangePassword", user.isMustChangePassword(),
                "expiresAt", Instant.now().plusMillis(ttl).toString()
        );
    }
}
