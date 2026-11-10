package com.museotek.box.application.screen;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
import com.museotek.box.infrastructure.repository.ScreenSetupCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
public class CancelScreenSetupCodeUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final ScreenSetupCodeRepository setupCodeRepository;
    private final Clock clock;

    public CancelScreenSetupCodeUseCase(OrgAccessGuard orgAccessGuard, ScreenSetupCodeRepository setupCodeRepository, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.setupCodeRepository = setupCodeRepository;
        this.clock = clock;
    }

    @Transactional
    public void execute(UUID orgId, UUID setupCodeId) {
        orgAccessGuard.requireManager(orgId);
        if (setupCodeRepository.cancel(setupCodeId, orgId, clock.instant()) == 0) {
            throw new SetupCodeNotFoundException("No open setup code " + setupCodeId + " for org " + orgId);
        }
    }
}
