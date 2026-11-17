package com.museotek.box.domain.display;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A TV or monitor next to one Box, showing that Box's DISPLAY experiences. A manager opens it from the CMS, which
 * gives the page a token of its own; the page then runs on that token, not on the manager's login.
 */
@Entity
@Table(name = "display_sessions")
@Getter
@Setter
@NoArgsConstructor
public class DisplaySession {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    // The one Box this display follows. The token can't reach any other Box.
    @Column(name = "box_id", nullable = false)
    private Long boxId;

    // SHA-256 (hex) of the token. The token itself is returned once, when the session is created.
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    // The JWT subject of the manager who opened the display.
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Updated when the token is used, at most once per museotek.display.last-seen-interval.
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    // Moves forward with last_seen_at: a display unused for museotek.display.session-lifetime stops working.
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    // Set when a manager revokes the display; the token stops working. The row stays.
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
