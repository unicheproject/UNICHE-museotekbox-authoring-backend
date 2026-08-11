package com.museotek.box.application.orgaccess;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueOrganisationDto;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Mandatory entry point for any org-scoped operation on data that is 100% local (no Catalogue
 * call of its own), the way {@link com.museotek.box.application.projectaccess.ProjectAccessGuard}
 * is for project-scoped data. Unlike that guard, there is no local companion row to reconcile
 * here — no {@code Organisation} table exists in this service — so this is a pure access check:
 * Catalogue's {@code GET /organisations/{orgId}} is itself access-checked per caller
 * ("get an organisation the caller can reach"), so simply forwarding to it and letting a 403/404
 * propagate is a real authorization boundary, not a cosmetic one.
 */
@Service
public class OrgAccessGuard {

    private final CatalogueClient catalogueClient;

    public OrgAccessGuard(CatalogueClient catalogueClient) {
        this.catalogueClient = catalogueClient;
    }

    public CatalogueOrganisationDto requireAccess(UUID orgId) {
        return catalogueClient.getOrganisation(orgId);
    }
}
