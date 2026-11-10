package com.museotek.box.application.screen;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.domain.screen.Screen;
import com.museotek.box.infrastructure.repository.ScreenRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** The org's screens, by name, with when each was last seen. Removed screens are left out. */
@Service
public class ListScreensQuery {

    private final OrgAccessGuard orgAccessGuard;
    private final ScreenRepository screenRepository;

    public ListScreensQuery(OrgAccessGuard orgAccessGuard, ScreenRepository screenRepository) {
        this.orgAccessGuard = orgAccessGuard;
        this.screenRepository = screenRepository;
    }

    public List<Screen> execute(UUID orgId) {
        orgAccessGuard.requireAccess(orgId);
        return screenRepository.findByOrgIdAndRevokedAtIsNullOrderByNameAsc(orgId);
    }
}
