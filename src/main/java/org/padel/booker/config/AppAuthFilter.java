package org.padel.booker.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.padel.booker.auth.AppTokenService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Requires a valid app session token (Bearer) on every /api call, except the login endpoint and
 * CORS preflight. Fails closed. This is what makes it safe to expose the app publicly.
 */
@Component
public class AppAuthFilter extends OncePerRequestFilter {

    private final AppTokenService tokens;

    public AppAuthFilter(AppTokenService tokens) {
        this.tokens = tokens;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String path = req.getServletPath();

        // Only guard the API; let anything else (shouldn't reach here behind nginx) pass.
        if (path == null || !path.startsWith("/api")) {
            chain.doFilter(req, res);
            return;
        }
        // Allow login and CORS preflight without a token.
        if (path.equals("/api/app-login") || "OPTIONS".equalsIgnoreCase(req.getMethod())) {
            chain.doFilter(req, res);
            return;
        }

        String auth = req.getHeader("Authorization");
        String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
        if (tokens.verify(token).isPresent()) {
            chain.doFilter(req, res);
            return;
        }

        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("{\"message\":\"Unauthorized\"}");
    }
}
