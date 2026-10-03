package org.padel.booker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the upstream Sporty Plus / tenis.plus API and the midnight booking burst.
 */
@ConfigurationProperties(prefix = "sporty")
public class SportyProperties {

    /** Auth host, e.g. https://api.sporty.plus/api/v3 */
    private String authBaseUrl;
    /** Data host, e.g. https://api.tenis.plus/v2 */
    private String apiBaseUrl;
    /** Origin header the upstream expects, e.g. https://modesto.sporty.plus */
    private String origin;
    /** The aggregator-portal origin; /v2/reservation under it returns a player's bookings across ALL clubs. */
    private String portalOrigin = "https://my.sporty.plus";
    private String language = "en";
    private long clubId;
    private String timezone = "Europe/Zagreb";
    private int maxReservationSpanDays = 7;
    private String reservationVersion = "20250422";

    private Booking booking = new Booking();

    public static class Booking {
        /**
         * When to fire the FIRST attempt relative to the server's midnight, in ms.
         * Positive = a bit AFTER midnight (so the slot is already open on the first try).
         * Negative = before midnight (bursting across the boundary).
         */
        private long firstAttemptAfterMs = 200;
        private long intervalMs = 250;
        private long giveUpMs = 15000;
        private int clockSyncLeadSeconds = 45;
        private int preRollSeconds = 5;

        public long getFirstAttemptAfterMs() { return firstAttemptAfterMs; }
        public void setFirstAttemptAfterMs(long v) { this.firstAttemptAfterMs = v; }
        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
        public long getGiveUpMs() { return giveUpMs; }
        public void setGiveUpMs(long giveUpMs) { this.giveUpMs = giveUpMs; }
        public int getClockSyncLeadSeconds() { return clockSyncLeadSeconds; }
        public void setClockSyncLeadSeconds(int v) { this.clockSyncLeadSeconds = v; }
        public int getPreRollSeconds() { return preRollSeconds; }
        public void setPreRollSeconds(int v) { this.preRollSeconds = v; }
    }

    public String getAuthBaseUrl() { return authBaseUrl; }
    public void setAuthBaseUrl(String v) { this.authBaseUrl = v; }
    public String getApiBaseUrl() { return apiBaseUrl; }
    public void setApiBaseUrl(String v) { this.apiBaseUrl = v; }
    public String getOrigin() { return origin; }
    public void setOrigin(String v) { this.origin = v; }
    public String getPortalOrigin() { return portalOrigin; }
    public void setPortalOrigin(String v) { this.portalOrigin = v; }
    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }
    public long getClubId() { return clubId; }
    public void setClubId(long v) { this.clubId = v; }
    public String getTimezone() { return timezone; }
    public void setTimezone(String v) { this.timezone = v; }
    public int getMaxReservationSpanDays() { return maxReservationSpanDays; }
    public void setMaxReservationSpanDays(int v) { this.maxReservationSpanDays = v; }
    public String getReservationVersion() { return reservationVersion; }
    public void setReservationVersion(String v) { this.reservationVersion = v; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
}
