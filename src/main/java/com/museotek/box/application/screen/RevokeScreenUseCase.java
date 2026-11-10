package com.museotek.box.application.screen;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.domain.screen.ScreenNotFoundException;
import com.museotek.box.infrastructure.repository.ScreenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * "Remove screen": its key stops working. The player project finds out at its next check of that key, so a connected
 * screen is closed within a few minutes.
 */
@Service
public class RevokeScreenUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScreenRepository screenRepository;
    private final Clock clock;

    public RevokeScreenUseCase(OrgAccessGuard orgAccessGuard, ScreenRepository screenRepository, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.screenRepository = screenRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID orgId, UUID screenId) {
        orgAccessGuard.requireManager(orgId);
        Screen screen = screenRepository.findByIdAndOrgIdAndRevokedAtIsNull(screenId, orgId)
                .orElseThrow(() -> new ScreenNotFoundException("No screen " + screenId + " for org " + orgId));
        screen.setRevokedAt(clock.instant());
    }
}
