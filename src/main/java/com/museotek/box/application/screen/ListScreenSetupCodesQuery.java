package com.museotek.box.application.screen;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.screen.ScreenSetupCode;
import com.museotek.box.infrastructure.repository.ScreenSetupCodeRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** The org's open setup codes (not used, cancelled or expired), newest first, so a manager can cancel one. */
@Service
public class ListScreenSetupCodesQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final ScreenSetupCodeRepository setupCodeRepository;
    private final Clock clock;

    public ListScreenSetupCodesQuery(OrgAccessGuard orgAccessGuard, ScreenSetupCodeRepository setupCodeRepository, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.setupCodeRepository = setupCodeRepository;
        this.clock = clock;
    }

    public List<ScreenSetupCode> execute(UUID orgId) {
        orgAccessGuard.requireManager(orgId);
        return setupCodeRepository.findOpen(orgId, clock.instant());
    }
}
