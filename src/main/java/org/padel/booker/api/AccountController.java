package org.padel.booker.api;

import org.padel.booker.auth.AppUserService;
import org.padel.booker.auth.UserContext;
import org.padel.booker.auth.UserView;
import org.padel.booker.domain.AppUser;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AppUserService users;

    public AccountController(AppUserService users) {
        this.users = users;
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    @GetMapping
    public UserView me() {
        AppUser u = users.require(UserContext.require());
        return UserView.of(u, users.hasSportyLinked(u.getId()));
    }

    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest req) {
        users.changePassword(UserContext.require(), req.currentPassword(), req.newPassword());
        return Map.of("ok", true);
    }
}
