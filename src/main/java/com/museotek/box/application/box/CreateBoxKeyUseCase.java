package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.application.secret.SecretTokens;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * A manager makes a login key for a Box, to install on it. The key is returned once, here only. A Box has one key: a
 * new one replaces the old, which stops working at once.
 */
@Service
public class CreateBoxKeyUseCase {

    public record CreatedBoxKey(String key, Instant createdAt) {
    }

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CreateBoxKeyUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository,
                               ApplicationEventPublisher events, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public CreatedBoxKey execute(UUID orgId, Long boxId) {
        orgAccessGuard.requireManager(orgId);
        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));
        boolean replacing = box.getKeyHash() != null;
        String key = SecretTokens.newBoxKey();
        Instant now = clock.instant();
        box.setKeyHash(SecretTokens.boxKeyHash(key));
        box.setKeyCreatedAt(now);
        if (replacing) {
            events.publishEvent(new BoxKeyChangedEvent(boxId));
        }
        return new CreatedBoxKey(key, now);
    }
}
