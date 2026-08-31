package org.padel.booker.api;

import tools.jackson.databind.JsonNode;
import org.padel.booker.auth.AuthService;
import org.padel.booker.client.SportyApiClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final AuthService auth;
    private final SportyApiClient api;

    public HealthController(AuthService auth, SportyApiClient api) {
        this.auth = auth;
        this.api = api;
    }

    /** Reports whether credentials are set and whether the token currently works (via /me). */
    @GetMapping
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean configured = auth.isConfigured();
        out.put("configured", configured);
        if (!configured) {
            out.put("tokenValid", false);
            out.put("message", "No credentials configured");
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
