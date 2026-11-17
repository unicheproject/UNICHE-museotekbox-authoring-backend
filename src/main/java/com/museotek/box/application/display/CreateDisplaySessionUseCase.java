package com.museotek.box.application.display;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import com.museotek.box.infrastructure.security.CurrentPrincipal;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * "Open display": a manager gets a token for a new display of one Box. The token is returned once, here only; the
 * display page keeps it and runs on it from then on.
 */
@Service
public class CreateDisplaySessionUseCase {

    public record CreatedDisplaySession(DisplaySession session, String token) {
    }

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;
    private final DisplaySessionRepository sessionRepository;
    private final DisplaySessionProperties properties;
    private final Clock clock;

    public CreateDisplaySessionUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository,
                                       DisplaySessionRepository sessionRepository,
                                       DisplaySessionProperties properties, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
        this.sessionRepository = sessionRepository;
        this.properties = properties;
        this.clock = clock;
    }

    public CreatedDisplaySession execute(UUID orgId, Long boxId) {
        orgAccessGuard.requireManager(orgId);
        boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        String token = SecretTokens.newToken();
        Instant now = clock.instant();
        DisplaySession session = new DisplaySession();
        session.setId(UUID.randomUUID());
        session.setBoxId(boxId);
        session.setTokenHash(SecretTokens.sha256Hex(token));
        session.setCreatedBy(CurrentPrincipal.subject().orElse(null));
        session.setCreatedAt(now);
        session.setExpiresAt(now.plus(properties.sessionLifetime()));
        return new CreatedDisplaySession(sessionRepository.save(session), token);
    }
}
