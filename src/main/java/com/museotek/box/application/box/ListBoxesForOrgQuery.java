package com.museotek.box.application.box;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.box.Box;
import com.museotek.box.infrastructure.repository.BoxRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class ListBoxesForOrgQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final BoxRepository boxRepository;

    public ListBoxesForOrgQuery(OrgAccessGuard orgAccessGuard, BoxRepository boxRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.boxRepository = boxRepository;
    }

    public List<Box> execute(UUID orgId) {
        orgAccessGuard.requireAccess(orgId);
        return boxRepository.findByOrgId(orgId);
    }
}
