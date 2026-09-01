package org.padel.booker.auth;

import org.padel.booker.domain.Role;

public record TokenPrincipal(long userId, Role role) {}
