package com.museotek.box.domain.screen;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/** A museum PC's browser that shows DISPLAY experiences through the player project. */
@Entity
@Table(name = "screens")
@Getter
@Setter
@NoArgsConstructor
public class Screen {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // SHA-256 (hex) of the screen's key. The key itself is shown once, when the setup code is redeemed.
    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    // The JWT subject of the manager who created the setup code.
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // Set each time the player project verifies the key.
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    // Set when a manager removes the screen; the key stops working. The row stays.
    @Column(name = "revoked_at")
    private Instant revokedAt;
}
