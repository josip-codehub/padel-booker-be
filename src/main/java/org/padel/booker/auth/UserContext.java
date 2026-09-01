package org.padel.booker.auth;

/**
 * Holds the acting user's id for the current thread. Set by the auth filter for web requests and by
 * the booking executor before it fires a scheduled booking, so downstream code (esp. the Sporty API
 * client) knows whose account to act as.
 */
public final class UserContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {}

    public static void set(Long userId) {
        CURRENT.set(userId);
    }

    public static Long get() {
        return CURRENT.get();
    }

    public static long require() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new IllegalStateException("No acting user in context");
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
