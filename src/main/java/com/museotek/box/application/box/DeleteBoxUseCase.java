package com.museotek.box.application.box;

import com.museotek.box.application.display.DisplaySessionsRevokedEvent;
import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Deletes a Box together with its displays; their connections are closed once the delete commits. */
@Service
public class DeleteBoxUseCase {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;
    private final DisplaySessionRepository displaySessionRepository;
    private final ApplicationEventPublisher events;

    public DeleteBoxUseCase(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository,
                            DisplaySessionRepository displaySessionRepository, ApplicationEventPublisher events) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
        this.displaySessionRepository = displaySessionRepository;
        this.events = events;
    }

    @Transactional
    public void execute(UUID orgId, Long boxId) {
        orgAccessGuard.requireManager(orgId);

        Box box = boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));

        List<UUID> openDisplays = displaySessionRepository.findNotRevokedIds(boxId);
        displaySessionRepository.deleteByBoxId(boxId);
        boxRepository.delete(box);
        if (!openDisplays.isEmpty()) {
            events.publishEvent(new DisplaySessionsRevokedEvent(openDisplays));
        }
        // Its key went with it: a connected Box is closed.
        if (box.getKeyHash() != null) {
            events.publishEvent(new BoxKeyChangedEvent(boxId));
        }
    }
}
