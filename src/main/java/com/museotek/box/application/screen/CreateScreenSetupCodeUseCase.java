package com.museotek.box.application.screen;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.screen.ScreenSetupCode;
import com.museotek.box.infrastructure.repository.ScreenSetupCodeRepository;
import com.museotek.box.infrastructure.security.CurrentPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** "Add screen": a manager gets a one-time code for a new screen of their org. The code is returned once, here only. */
@Service
public class CreateScreenSetupCodeUseCase {

    public record CreatedSetupCode(ScreenSetupCode setupCode, String code) {
    }

    private final OrgAccessGuard orgAccessGuard;
    private final ScreenSetupCodeRepository setupCodeRepository;
    private final Clock clock;
    private final Duration validFor;

    public CreateScreenSetupCodeUseCase(
            OrgAccessGuard orgAccessGuard,
            ScreenSetupCodeRepository setupCodeRepository,
            Clock clock,
            @Value("${museotek.screens.setup-code-valid-days}") int validDays
    ) {
        this.orgAccessGuard = orgAccessGuard;
        this.setupCodeRepository = setupCodeRepository;
        this.clock = clock;
        this.validFor = Duration.ofDays(validDays);
    }

    public CreatedSetupCode execute(UUID orgId, String screenName) {
        orgAccessGuard.requireManager(orgId);

        String code = ScreenSecrets.newSetupCode();
        Instant now = clock.instant();
        ScreenSetupCode setupCode = new ScreenSetupCode();
        setupCode.setId(UUID.randomUUID());
        setupCode.setOrgId(orgId);
        setupCode.setScreenName(screenName.trim());
        setupCode.setCodeHash(ScreenSecrets.sha256Hex(ScreenSecrets.normaliseSetupCode(code)));
        setupCode.setCreatedBy(CurrentPrincipal.subject().orElse(null));
        setupCode.setCreatedAt(now);
        setupCode.setExpiresAt(now.plus(validFor));
        return new CreatedSetupCode(setupCodeRepository.save(setupCode), code);
    }
}
