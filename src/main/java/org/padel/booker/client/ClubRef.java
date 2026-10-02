package org.padel.booker.client;

/**
 * The minimal club context a Sporty API call needs: which club to filter by and which
 * {@code origin} header to send (the upstream brands/scopes responses by origin).
 */
public record ClubRef(long clubId, String origin) {}
