package org.padel.booker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.padel.booker.auth.AppTokenService;
import org.padel.booker.auth.TokenPrincipal;
import org.padel.booker.auth.UserContext;
import org.padel.booker.domain.AppUser;
import org.padel.booker.domain.Role;
import org.padel.booker.repo.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Guards /api: requires a valid token, sets the acting user in {@link UserContext}, enforces the
 * admin role on /api/admin/**, and blocks everything except the password-change endpoints while a
 * user still must change their password. Login and CORS preflight pass through.
 */
@Component
public class AppAuthFilter extends OncePerRequestFilter {

    private final AppTokenService tokens;
    private final AppUserRepository users;

    public AppAuthFilter(AppTokenService tokens, AppUserRepository users) {
        this.tokens = tokens;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();

        if (path == null || !path.startsWith("/api")) {
            chain.doFilter(req, res);
            return;
        }
        if (path.equals("/api/app-login") || "OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        Optional<TokenPrincipal> principal = extractToken(req).flatMap(tokens::verify);
        if (principal.isEmpty()) {
            deny(res, 401, "Unauthorized", null);
            return;
        }
        AppUser user = users.findById(principal.get().userId()).orElse(null);
        if (user == null) {
            deny(res, 401, "Unauthorized", null);
            return;
        }

        // Admin-only area.
        if (path.startsWith("/api/admin") && user.getRole() != Role.ADMIN) {
            deny(res, 403, "Admins only", null);
            return;
        }
        // Force password change: only account endpoints allowed until it's done.
        if (user.isMustChangePassword() && !path.startsWith("/api/account")) {
            deny(res, 403, "Password change required", "PASSWORD_CHANGE_REQUIRED");
            return;
        }

        try {
            UserContext.set(user.getId());
            chain.doFilter(req, res);
        } finally {
            UserContext.clear();
        }
    }

    private Optional<String> extractToken(HttpServletRequest req) {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) return Optional.of(auth.substring(7));
        return Optional.empty();
    }

    private void deny(HttpServletResponse res, int status, String message, String code) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        String body = code == null
                ? "{\"message\":\"" + message + "\"}"
                : "{\"message\":\"" + message + "\",\"code\":\"" + code + "\"}";
        res.getWriter().write(body);
    }
}
