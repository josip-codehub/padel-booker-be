package org.padel.booker.api;

import tools.jackson.databind.JsonNode;
import org.padel.booker.auth.SportyAccountService;
import org.padel.booker.auth.UserContext;
import org.padel.booker.client.SportyApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** Whether the CURRENT user's Sporty account is linked and its token currently works. */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final SportyAccountService accounts;
    private final SportyApiClient api;

    public HealthController(SportyAccountService accounts, SportyApiClient api) {
        this.accounts = accounts;
        this.api = api;
    }

    @GetMapping
    public Map<String, Object> health() {
        long userId = UserContext.require();
        Map<String, Object> out = new LinkedHashMap<>();
        boolean configured = accounts.isConfigured(userId);
        out.put("configured", configured);
        if (!configured) {
            out.put("tokenValid", false);
            out.put("message", "No Sporty account linked");
            return out;
        }
        try {
            JsonNode me = api.me();
            out.put("tokenValid", true);
            out.put("user", me.path("user").path("display_name").asText(null));
        } catch (RuntimeException e) {
            out.put("tokenValid", false);
            out.put("message", e.getMessage());
        }
        return out;
    }
}
