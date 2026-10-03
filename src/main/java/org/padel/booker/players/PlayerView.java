package org.padel.booker.players;

/** A person found via search, who can be added to a booking. */
public record PlayerView(long teamId, long playerId, String name, String image) {}
