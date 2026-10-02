package org.padel.booker.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An admin's standing weekly booking: the same court + time slots, every week, until cancelled.
 * Each week a {@link QueuedBooking} occurrence is spawned to fire at the club's midnight opening.
 * While active it reserves its slot against other users for every future matching week.
 */
@Entity
@Table(name = "fixed_bookings")
public class FixedBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The admin who owns this standing booking (fires under their Sporty token). */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "club_id", nullable = false)
    private Long clubId;

    @Column(nullable = false)
    private Long courtId;

    private String courtName;

    /** ISO day-of-week 1=Mon .. 7=Sun (matches java.time.DayOfWeek#getValue). */
    @Column(nullable = false)
    private int weekday;

    /** Earliest play date this rule applies from (occurrences are not spawned before it). */
    private java.time.LocalDate startDate;

    /** First slot start code (decimal-hours x100). */
    @Column(nullable = false)
    private int slotTime;

    private String slotTimeFmt;
    private String slotEndFmt;

    @Column(nullable = false)
    private int slotCount = 1;

    private int slotMinutes;

    /** All slot start codes, comma-separated. */
    @Column(length = 500)
    private String slotTimesCsv;

    /** The {@code times[]} template (one object per slot, with price), serialized as JSON. */
    @Column(columnDefinition = "text")
    private String slotsJson;

    /** The players array to POST, serialized as JSON (captured at creation). */
    @Column(columnDefinition = "text")
    private String playersJson;

    private java.math.BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    @Transient
    public List<Integer> slotCodes() {
        List<Integer> out = new ArrayList<>();
        if (slotTimesCsv != null && !slotTimesCsv.isBlank()) {
            for (String p : slotTimesCsv.split(",")) {
                String s = p.trim();
                if (!s.isEmpty()) {
                    try { out.add(Integer.parseInt(s)); } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (out.isEmpty()) out.add(slotTime);
        return out;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getClubId() { return clubId; }
    public void setClubId(Long v) { this.clubId = v; }
    public Long getCourtId() { return courtId; }
    public void setCourtId(Long v) { this.courtId = v; }
    public String getCourtName() { return courtName; }
    public void setCourtName(String v) { this.courtName = v; }
    public int getWeekday() { return weekday; }
    public void setWeekday(int v) { this.weekday = v; }
    public java.time.LocalDate getStartDate() { return startDate; }
    public void setStartDate(java.time.LocalDate v) { this.startDate = v; }
    public int getSlotTime() { return slotTime; }
    public void setSlotTime(int v) { this.slotTime = v; }
    public String getSlotTimeFmt() { return slotTimeFmt; }
    public void setSlotTimeFmt(String v) { this.slotTimeFmt = v; }
    public String getSlotEndFmt() { return slotEndFmt; }
    public void setSlotEndFmt(String v) { this.slotEndFmt = v; }
    public int getSlotCount() { return slotCount; }
    public void setSlotCount(int v) { this.slotCount = v; }
    public int getSlotMinutes() { return slotMinutes; }
    public void setSlotMinutes(int v) { this.slotMinutes = v; }
    public String getSlotTimesCsv() { return slotTimesCsv; }
    public void setSlotTimesCsv(String v) { this.slotTimesCsv = v; }
    public String getSlotsJson() { return slotsJson; }
    public void setSlotsJson(String v) { this.slotsJson = v; }
    public String getPlayersJson() { return playersJson; }
    public void setPlayersJson(String v) { this.playersJson = v; }
    public java.math.BigDecimal getPrice() { return price; }
    public void setPrice(java.math.BigDecimal v) { this.price = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
