package com.museotek.box.application.player;

import com.museotek.box.application.screen.ScreenSecrets;
import com.museotek.box.domain.screen.InvalidScreenKeyException;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.infrastructure.repository.ScreenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/** Checks a screen's key when it logs in to the player. Marks the screen seen. */
@Service
public class VerifyScreenKeyUseCase {

    private final ScreenRepository screenRepository;
    private final Clock clock;

    public VerifyScreenKeyUseCase(ScreenRepository screenRepository, Clock clock) {
        this.screenRepository = screenRepository;
        this.clock = clock;
    }

    @Transactional
    public PlayerScreenView execute(String key) {
        Screen screen = screenRepository.findByKeyHashAndRevokedAtIsNull(ScreenSecrets.sha256Hex(key))
                .orElseThrow(() -> new InvalidScreenKeyException("Unknown or revoked screen key"));
        screen.setLastSeenAt(clock.instant());
        return PlayerScreenView.from(screen);
    }
}
