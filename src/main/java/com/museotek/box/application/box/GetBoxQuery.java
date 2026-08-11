package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxNotFoundException;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GetBoxQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;

    public GetBoxQuery(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
    }

    public Box execute(UUID orgId, Long boxId) {
        orgAccessGuard.requireAccess(orgId);
        return boxRepository.findByIdAndOrgId(boxId, orgId)
                .orElseThrow(() -> new BoxNotFoundException("No box " + boxId + " for org " + orgId));
    }
}
