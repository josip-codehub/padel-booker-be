package org.padel.booker.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * A supported club a given app user is a member of, with that user's advance-booking window
 * (fetched from Sporty per account, since it can differ by membership). Refreshed when the user
 * links/updates their Sporty account.
 */
@Entity
@Table(name = "user_clubs", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "club_id"}))
public class UserClub {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The Sporty club id (matches the registry). */
    @Column(name = "club_id", nullable = false)
    private Long clubId;

    /** Advance-booking window in days for THIS user at THIS club (from /me). */
    @Column(nullable = false)
    private int maxSpanDays;

    /** Whether the user is currently a member (false = left the club / no longer visible). */
    @Column(nullable = false)
    private boolean enabled = true;

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
    public Long getClubId() { return clubId; }
    public void setClubId(Long v) { this.clubId = v; }
    public int getMaxSpanDays() { return maxSpanDays; }
    public void setMaxSpanDays(int v) { this.maxSpanDays = v; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean v) { this.enabled = v; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant v) { this.updatedAt = v; }
}
