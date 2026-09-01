package org.padel.booker.auth;

import org.padel.booker.domain.AppUser;

import java.time.Instant;

public record UserView(
        long id,
        String username,
        String role,
        boolean mustChangePassword,
        boolean sportyLinked,
        Instant createdAt
) {
    public static UserView of(AppUser u, boolean sportyLinked) {
        return new UserView(u.getId(), u.getUsername(), u.getRole().name(),
                u.isMustChangePassword(), sportyLinked, u.getCreatedAt());
    }
}
