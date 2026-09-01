package org.padel.booker.api;

import org.padel.booker.auth.AppUserService;
import org.padel.booker.auth.UserContext;
import org.padel.booker.auth.UserView;
import org.padel.booker.domain.AppUser;
import org.padel.booker.domain.Role;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AppUserService users;

    public AdminUserController(AppUserService users) {
        this.users = users;
    }

    public record CreateUserRequest(String username, String role) {}

    @GetMapping
    public List<UserView> list() {
        return users.list().stream()
                .map(u -> UserView.of(u, users.hasSportyLinked(u.getId())))
                .toList();
    }

    @PostMapping
    public UserView create(@RequestBody CreateUserRequest req) {
        Role role = "ADMIN".equalsIgnoreCase(req.role()) ? Role.ADMIN : Role.USER;
        AppUser u = users.create(req.username(), role);
        return UserView.of(u, false);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        users.delete(UserContext.require(), id);
        return Map.of("deleted", true, "id", id);
    }

    @PostMapping("/{id}/reset-password")
    public Map<String, Object> resetPassword(@PathVariable long id) {
        users.resetPassword(id);
        return Map.of("reset", true, "id", id, "temporaryPassword", AppUserService.RESET_PASSWORD);
    }
}
