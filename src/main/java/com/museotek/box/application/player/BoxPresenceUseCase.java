package com.museotek.box.application.player;

import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * A Box's online status, from its socket: online while connected, offline once it drops. What it is playing is
 * cleared either way; the Box's own state messages set it.
 * <p>
 * Each write runs in a transaction of its own ({@code REQUIRES_NEW}): it is also called from after-commit listeners
 * (a replaced key closes the Box), where the finished transaction is still bound and joining it would fail with
 * "no transaction is in progress".
 */
@Service
public class BoxPresenceUseCase {

    private final BoxRepository boxRepository;
    private final Clock clock;

    public BoxPresenceUseCase(BoxRepository boxRepository, Clock clock) {
        this.boxRepository = boxRepository;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOnline(Long boxId) {
        boxRepository.setPresence(boxId, BoxStatus.ONLINE, clock.instant());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markOffline(Long boxId) {
        boxRepository.setPresence(boxId, BoxStatus.OFFLINE, clock.instant());
    }

    /** At startup no Box is connected yet, whatever the table says from before a restart or crash. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markAllOffline() {
        return boxRepository.markAllOffline(BoxStatus.OFFLINE, clock.instant());
    }
}
