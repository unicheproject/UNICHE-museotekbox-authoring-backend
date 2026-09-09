package com.museotek.box.application.project;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GetProjectQueryTest {

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final GetProjectQuery query = new GetProjectQuery(projectAccessGuard);

    @Test
    void execute_delegatesToProjectAccessGuard() {
        UUID id = UUID.randomUUID();
        CatalogueProjectDto dto = new CatalogueProjectDto(
                id.toString(), UUID.randomUUID().toString(), "Ancient Egypt Wing", "ancient-egypt", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
        when(projectAccessGuard.requireAccess(id)).thenReturn(dto);

        CatalogueProjectDto result = query.execute(id);

        assertThat(result).isSameAs(dto);
        verify(projectAccessGuard).requireAccess(id);
    }
}
