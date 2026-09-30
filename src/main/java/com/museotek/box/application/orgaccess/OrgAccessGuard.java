package com.museotek.box.application.orgaccess;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;
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

    /**
     * {@link #requireAccess(UUID)} plus: the caller must manage this org or be a platform admin.
     * Access is checked first so a caller with no relation to the org still gets Catalogue's 404,
     * not a 403 that would confirm the org exists. The role comes from Catalogue's
     * {@code /me/authorization}, which {@link CatalogueClient} caches per caller.
     */
    public CatalogueOrganisationDto requireManager(UUID orgId) {
        CatalogueOrganisationDto organisation = requireAccess(orgId);

        CatalogueMeAuthorizationDto me = catalogueClient.getMeAuthorization();
        if (!me.platformAdmin() && !me.managedOrganisations().contains(orgId.toString())) {
            throw new OrgManagerRequiredException("Only a manager of org " + orgId + " can do this");
        }
        return organisation;
    }
}
