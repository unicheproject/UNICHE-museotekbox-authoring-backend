package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** A manager removes a Box's key (e.g. the Box was stolen): it can't log in until it gets a new one. */
@Service
public class RemoveBoxKeyUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;
    private final ApplicationEventPublisher events;

    public RemoveBoxKeyUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository,
                               ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
        this.events = events;
    }

    // Removing a key the Box doesn't have is fine: the result is the same.
    @Transactional
    public void execute(UUID orgId, Long boxId) {
        orgAccessGuard.requireManager(orgId);
        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));
        if (box.getKeyHash() == null) {
            return;
        }
        box.setKeyHash(null);
        box.setKeyCreatedAt(null);
        events.publishEvent(new BoxKeyChangedEvent(boxId));
    }
}
