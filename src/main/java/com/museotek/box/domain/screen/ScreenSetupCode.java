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

/**
 * A one-time code from "Add screen". Open while not used, not cancelled and not expired. The player project redeems it
 * once, which creates the screen.
 */
@Entity
@Table(name = "screen_setup_codes")
@Getter
@Setter
@NoArgsConstructor
public class ScreenSetupCode {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    // The name the screen gets when the code is redeemed.
    @Column(name = "screen_name", nullable = false, length = 255)
    private String screenName;

    // SHA-256 (hex) of the code. The code itself is shown once, when it's created.
    @Column(name = "code_hash", nullable = false, unique = true, length = 64)
    private String codeHash;

    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    // The screen this code became.
    @Column(name = "screen_id")
    private UUID screenId;
}
