package com.museotek.box.application.display;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.domain.display.DisplaySession;
import com.museotek.box.infrastructure.repository.BoxRepository;
import com.museotek.box.infrastructure.repository.DisplaySessionRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/** A Box's displays that still work (not revoked, not expired), oldest first. Any org member may see them. */
@Service
public class ListDisplaySessionsQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;
    private final DisplaySessionRepository sessionRepository;
    private final Clock clock;

    public ListDisplaySessionsQuery(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository,
                                    DisplaySessionRepository sessionRepository, Clock clock) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
        this.sessionRepository = sessionRepository;
        this.clock = clock;
    }

    public List<DisplaySession> execute(UUID orgId, Long boxId) {
        orgAccessGuard.requireAccess(orgId);
        boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));
        return sessionRepository.findActive(boxId, clock.instant());
    }
}
