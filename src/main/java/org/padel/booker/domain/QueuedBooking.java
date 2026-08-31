package org.padel.booker.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "queued_bookings")
public class QueuedBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long courtId;

    private String courtName;

    /** The day you want to play. */
    @Column(nullable = false)
    private LocalDate playDate;

    /** Slot start as HHMM, e.g. 700 = 07:00, 1430 = 14:30. */
    @Column(nullable = false)
    private int slotTime;

    /** Slot start as "HH:mm" for display. */
    private String slotTimeFmt;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public String getNote() { return note; }
    public void setNote(String v) { this.note = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
