package org.padel.booker.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "queued_bookings")
public class QueuedBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The app user who scheduled this booking. */
    @Column(name = "user_id")
    private Long userId;

    /** The Sporty club this booking is for (null on legacy rows = treated as Modesto). */
    @Column(name = "club_id")
    private Long clubId;

    @Column(nullable = false)
    private Long courtId;

    private String courtName;

    /** The day you want to play. */
    @Column(nullable = false)
    private LocalDate playDate;

    /** First slot start as a decimal-hours x100 code, e.g. 700 = 07:00, 2050 = 20:30. */
    @Column(nullable = false)
    private int slotTime;

    /** First slot start as "HH:mm" for display. */
    private String slotTimeFmt;

    /** End of the last slot as "HH:mm" (start + slotCount*slotMinutes), for display. */
    private String slotEndFmt;

    /** Number of consecutive slots this booking spans (Modesto 1, Silax 2..4). */
    @Column(nullable = false)
    private int slotCount = 1;

    /** Minutes per slot (court reservation_duration): 90 Modesto, 30 Silax. */
    private int slotMinutes;

    /** All slot start codes, comma-separated (e.g. "2000,2050,2100"). */
    @Column(length = 500)
    private String slotTimesCsv;

    /** The full {@code times[]} array to POST (one object per slot, with price), serialized as JSON. */
    @Column(columnDefinition = "text")
    private String slotsJson;

    private BigDecimal price;

    /** The full top-level "players" array to POST, serialized as JSON. Captured at queue time. */
    @Column(columnDefinition = "text", nullable = false)
    private String playersJson;

    /** The instant the slot opens: 00:00:00 club-time, (playDate - span) days before. */
    @Column(nullable = false)
    private Instant targetFireAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.PENDING;

    private Long createdReservationId;

    /** If set, this occurrence was spawned by a standing weekly {@link FixedBooking}. */
    @Column(name = "fixed_booking_id")
    private Long fixedBookingId;

    @Column(length = 500)
    private String note;

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

    /** The slot start codes this booking occupies (parsed from CSV; falls back to the single start). */
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
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getClubId() { return clubId; }
    public void setClubId(Long v) { this.clubId = v; }
    public Long getCourtId() { return courtId; }
    public void setCourtId(Long v) { this.courtId = v; }
    public String getCourtName() { return courtName; }
    public void setCourtName(String v) { this.courtName = v; }
    public LocalDate getPlayDate() { return playDate; }
    public void setPlayDate(LocalDate v) { this.playDate = v; }
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
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal v) { this.price = v; }
    public String getPlayersJson() { return playersJson; }
    public void setPlayersJson(String v) { this.playersJson = v; }
    public Instant getTargetFireAt() { return targetFireAt; }
    public void setTargetFireAt(Instant v) { this.targetFireAt = v; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus v) { this.status = v; }
    public Long getCreatedReservationId() { return createdReservationId; }
    public void setCreatedReservationId(Long v) { this.createdReservationId = v; }
    public Long getFixedBookingId() { return fixedBookingId; }
    public void setFixedBookingId(Long v) { this.fixedBookingId = v; }
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
