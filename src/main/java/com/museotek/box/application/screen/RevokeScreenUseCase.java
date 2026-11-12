package com.museotek.box.application.screen;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.domain.screen.ScreenNotFoundException;
import com.museotek.box.infrastructure.repository.ScreenRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * "Remove screen": its key stops working. A {@link ScreenRevokedEvent} lets the player close the screen's connection
 * straight away.
 */
@Service
public class RevokeScreenUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScreenRepository screenRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RevokeScreenUseCase(OrgAccessGuard orgAccessGuard, ScreenRepository screenRepository,
                               ApplicationEventPublisher events, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.screenRepository = screenRepository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID orgId, UUID screenId) {
        orgAccessGuard.requireManager(orgId);
        Screen screen = screenRepository.findByIdAndOrgIdAndRevokedAtIsNull(screenId, orgId)
                .orElseThrow(() -> new ScreenNotFoundException("No screen " + screenId + " for org " + orgId));
        screen.setRevokedAt(clock.instant());
        events.publishEvent(new ScreenRevokedEvent(screenId));
    }
}
