package org.padel.booker.players;

/** A participant currently on a reservation. */
public record ReservationPlayer(long playerId, String name, String image) {}
