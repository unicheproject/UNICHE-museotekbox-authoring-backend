package com.museotek.box.application.display;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.domain.display.DisplaySessionNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * A manager revokes a display: its token stops working. A {@link DisplaySessionsRevokedEvent} lets the player close
 * its connection straight away.
 */
@Service
public class RevokeDisplaySessionUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;
    private final DisplaySessionRepository sessionRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RevokeDisplaySessionUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository,
                                       DisplaySessionRepository sessionRepository,
                                       ApplicationEventPublisher events, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
        this.sessionRepository = sessionRepository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID orgId, Long boxId, UUID sessionId) {
        orgAccessGuard.requireManager(orgId);
        boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));
        DisplaySession session = sessionRepository.findByIdAndBoxIdAndRevokedAtIsNull(sessionId, boxId)
                .orElseThrow(() -> new DisplaySessionNotFoundException(
                        "No display " + sessionId + " for box " + boxId));
        session.setRevokedAt(clock.instant());
        events.publishEvent(new DisplaySessionsRevokedEvent(List.of(sessionId)));
    }
}
