package com.museotek.box.application.player;

import com.museotek.box.application.screen.ScreenSecrets;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.domain.screen.ScreenSetupCode;
import com.museotek.box.domain.screen.SetupCodeNotFoundException;
import com.museotek.box.infrastructure.repository.ScreenRepository;
import com.museotek.box.infrastructure.repository.ScreenSetupCodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Uses up a setup code and creates its screen. The new screen's key is returned once, here only; the player passes it
 * to the screen. Unknown, used, cancelled and expired codes all answer the same, so a guess learns nothing.
 */
@Service
public class RedeemScreenSetupCodeUseCase {

    public record RedeemedScreen(PlayerScreenView screen, String key) {
    }

    private final ScreenSetupCodeRepository setupCodeRepository;
    private final ScreenRepository screenRepository;
    private final Clock clock;

    public RedeemScreenSetupCodeUseCase(ScreenSetupCodeRepository setupCodeRepository, ScreenRepository screenRepository,
                                        Clock clock) {
        this.setupCodeRepository = setupCodeRepository;
        this.screenRepository = screenRepository;
        this.clock = clock;
    }

    @Transactional
    public RedeemedScreen execute(String typedCode) {
        String normalised = ScreenSecrets.normaliseSetupCode(typedCode);
        if (normalised.isEmpty()) {
            throw notFound();
        }
        String codeHash = ScreenSecrets.sha256Hex(normalised);
        Instant now = clock.instant();
        if (setupCodeRepository.markUsed(codeHash, now) == 0) {
            throw notFound();
        }
        ScreenSetupCode setupCode = setupCodeRepository.findByCodeHash(codeHash).orElseThrow(RedeemScreenSetupCodeUseCase::notFound);

        String key = ScreenSecrets.newScreenKey();
        Screen screen = new Screen();
        screen.setId(UUID.randomUUID());
        screen.setOrgId(setupCode.getOrgId());
        screen.setName(setupCode.getScreenName());
        screen.setKeyHash(ScreenSecrets.sha256Hex(key));
        screen.setCreatedBy(setupCode.getCreatedBy());
        screen.setCreatedAt(now);
        Screen saved = screenRepository.save(screen);
        setupCode.setScreenId(saved.getId());
        return new RedeemedScreen(PlayerScreenView.from(saved), key);
    }

    private static SetupCodeNotFoundException notFound() {
        return new SetupCodeNotFoundException("Unknown, used, cancelled or expired setup code");
    }
}
