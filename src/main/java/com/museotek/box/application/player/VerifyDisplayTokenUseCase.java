package com.museotek.box.application.player;

import com.museotek.box.application.display.DisplaySessionProperties;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.display.InvalidDisplayTokenException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Checks a display's token when it logs in or calls the display API. A used token moves its expiry forward, but
 * writes at most once per {@code last-seen-interval}, so a display in use all day doesn't write on every call.
 */
@Service
public class VerifyDisplayTokenUseCase {

    private final DisplaySessionRepository sessionRepository;
    private final BoxRepository boxRepository;
    private final DisplaySessionProperties properties;
    private final Clock clock;

    public VerifyDisplayTokenUseCase(DisplaySessionRepository sessionRepository, BoxRepository boxRepository,
                                     DisplaySessionProperties properties, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.boxRepository = boxRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public PlayerDisplayView execute(String token) {
        Instant now = clock.instant();
        DisplaySession session = sessionRepository.findByTokenHashAndRevokedAtIsNull(SecretTokens.sha256Hex(token))
                .orElseThrow(() -> new InvalidDisplayTokenException("Unknown or revoked display token"));
        if (!session.getExpiresAt().isAfter(now)) {
            throw new InvalidDisplayTokenException("Expired display token");
        }
        // Deleting a Box deletes its sessions in the same transaction, so this only fails in a race with that delete.
        Box box = boxRepository.findById(session.getBoxId())
                .orElseThrow(() -> new InvalidDisplayTokenException("The display's box no longer exists"));
        Instant lastSeen = session.getLastSeenAt();
        if (lastSeen == null || !lastSeen.plus(properties.lastSeenInterval()).isAfter(now)) {
            session.setLastSeenAt(now);
            session.setExpiresAt(now.plus(properties.sessionLifetime()));
        }
        return new PlayerDisplayView(session.getId(), box.getId(), box.getOrgId(), session.getOrgName());
    }
}
