package org.padel.booker.api;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.padel.booker.auth.UserContext;
import org.padel.booker.domain.UserPrefs;
import org.padel.booker.repo.UserPrefsRepository;
import org.springframework.web.bind.annotation.*;

/**
 * Per-user UI preferences (opaque JSON the frontend owns): per-club visible/default courts, and the
 * default club/court selection for the availability screen.
 */
@RestController
@RequestMapping("/api/account")
public class PrefsController {

    private final UserPrefsRepository repo;
    private final ObjectMapper mapper;

    public PrefsController(UserPrefsRepository repo, ObjectMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @GetMapping("/court-prefs")
    public JsonNode getCourtPrefs() {
        return read(repo.findByUserId(UserContext.require()).map(UserPrefs::getCourtPrefsJson).orElse(null));
    }

    @PutMapping("/court-prefs")
    public JsonNode putCourtPrefs(@RequestBody JsonNode body) {
        UserPrefs p = load();
        p.setCourtPrefsJson(body == null ? null : body.toString());
        repo.save(p);
        return body != null ? body : mapper.createObjectNode();
    }

    @GetMapping("/availability-prefs")
    public JsonNode getAvailabilityPrefs() {
        return read(repo.findByUserId(UserContext.require()).map(UserPrefs::getAvailabilityPrefsJson).orElse(null));
    }

    @PutMapping("/availability-prefs")
    public JsonNode putAvailabilityPrefs(@RequestBody JsonNode body) {
        UserPrefs p = load();
        p.setAvailabilityPrefsJson(body == null ? null : body.toString());
        repo.save(p);
        return body != null ? body : mapper.createObjectNode();
    }

    private UserPrefs load() {
        long userId = UserContext.require();
        return repo.findByUserId(userId).orElseGet(() -> {
            UserPrefs fresh = new UserPrefs();
            fresh.setUserId(userId);
            return fresh;
        });
    }

    private JsonNode read(String json) {
        if (json == null || json.isBlank()) return mapper.createObjectNode();
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
