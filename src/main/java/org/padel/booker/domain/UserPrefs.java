package org.padel.booker.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Per-user UI preferences, stored as opaque JSON the frontend owns:
 * - {@code courtPrefsJson}: per-club visible courts + default court selection.
 * - {@code availabilityPrefsJson}: the default club/court selection for the availability screen.
 */
@Entity
@Table(name = "user_prefs")
public class UserPrefs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(columnDefinition = "text")
    private String courtPrefsJson;

    @Column(columnDefinition = "text")
    private String availabilityPrefsJson;

    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getCourtPrefsJson() { return courtPrefsJson; }
    public void setCourtPrefsJson(String v) { this.courtPrefsJson = v; }
    public String getAvailabilityPrefsJson() { return availabilityPrefsJson; }
    public void setAvailabilityPrefsJson(String v) { this.availabilityPrefsJson = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
