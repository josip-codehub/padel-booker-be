package org.padel.booker.club;

import org.padel.booker.client.ClubRef;

/**
 * A club we have verified and support. Rules differ per club and we only trust the ones we've
 * confirmed from real traffic, so this is a small hand-maintained registry (see {@link ClubRegistry})
 * rather than anything auto-trusted from the API.
 *
 * - {@code defaultSlotMinutes}: the court granularity we expect (actual value is still read per-court
 *   from the availability response; this is only a hint/fallback).
 * - {@code minSlots}/{@code maxSlots}: how many consecutive slots a single booking may span
 *   (Modesto = exactly one 90-min slot; Silax = 2..4 thirty-min slots).
 * - {@code fallbackSpanDays}: advance-booking window to use if {@code /me} doesn't report one.
 * - {@code latitude}/{@code longitude}: club location for the weather forecast (from Sporty's club data).
 */
public record SupportedClub(
        long clubId,
        String name,
        String origin,
        int defaultSlotMinutes,
        int minSlots,
        int maxSlots,
        int fallbackSpanDays,
        double latitude,
        double longitude
) {
    public ClubRef ref() {
        return new ClubRef(clubId, origin);
    }
}
