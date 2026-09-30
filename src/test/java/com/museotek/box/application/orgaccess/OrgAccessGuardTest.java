package com.museotek.box.application.orgaccess;

import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueMeAuthorizationDto;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.catalogue.CatalogueOrganisationDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrgAccessGuardTest {

    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final OrgAccessGuard guard = new OrgAccessGuard(catalogueClient);

    @Test
    void success_returnsOrganisationFromCatalogue() {
        UUID orgId = UUID.randomUUID();
        CatalogueOrganisationDto dto = new CatalogueOrganisationDto(orgId.toString(), "Mobics", "mobics", "ACTIVE");
        when(catalogueClient.getOrganisation(orgId)).thenReturn(dto);

        CatalogueOrganisationDto result = guard.requireAccess(orgId);

        assertThat(result).isSameAs(dto);
    }

    @Test
    void catalogueForbidden_propagatesUnchanged() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getOrganisation(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> guard.requireAccess(orgId)).isInstanceOf(CatalogueForbiddenException.class);
    }

    @Test
    void catalogueNotFound_propagatesUnchanged() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getOrganisation(orgId)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> guard.requireAccess(orgId)).isInstanceOf(CatalogueNotFoundException.class);
    }

    @Test
    void requireManager_managerOfOrg_returnsOrganisation() {
        UUID orgId = UUID.randomUUID();
        CatalogueOrganisationDto dto = new CatalogueOrganisationDto(orgId.toString(), "Mobics", "mobics", "ACTIVE");
        when(catalogueClient.getOrganisation(orgId)).thenReturn(dto);
        when(catalogueClient.getMeAuthorization()).thenReturn(me(false, List.of(orgId.toString())));

        CatalogueOrganisationDto result = guard.requireManager(orgId);

        assertThat(result).isSameAs(dto);
    }

    @Test
    void requireManager_platformAdmin_passesWithoutManagingOrg() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getMeAuthorization()).thenReturn(me(true, List.of()));

        guard.requireManager(orgId);
    }

    @Test
    void requireManager_managerOfAnotherOrgOnly_throws() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getMeAuthorization()).thenReturn(me(false, List.of(UUID.randomUUID().toString())));

        assertThatThrownBy(() -> guard.requireManager(orgId)).isInstanceOf(OrgManagerRequiredException.class);
    }

    @Test
    void requireManager_noOrgAccess_propagatesBeforeCheckingRole() {
        UUID orgId = UUID.randomUUID();
        when(catalogueClient.getOrganisation(orgId)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> guard.requireManager(orgId)).isInstanceOf(CatalogueNotFoundException.class);

        verify(catalogueClient, never()).getMeAuthorization();
    }

    private static CatalogueMeAuthorizationDto me(boolean platformAdmin, List<String> managedOrganisations) {
        return new CatalogueMeAuthorizationDto("user-1", platformAdmin, managedOrganisations, List.of(), "etag", 60);
    }
}
