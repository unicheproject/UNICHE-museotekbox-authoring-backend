package com.museotek.box.application.comment;

import com.museotek.box.application.orgaccess.OrgAccessGuard;
import com.museotek.box.infrastructure.catalogue.CatalogueClient;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.CommentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CountOpenCommentsQueryTest {

    private final OrgAccessGuard orgAccessGuard = mock(OrgAccessGuard.class);
    private final CatalogueClient catalogueClient = mock(CatalogueClient.class);
    private final CommentRepository commentRepository = mock(CommentRepository.class);
    private final CountOpenCommentsQuery query = new CountOpenCommentsQuery(orgAccessGuard, catalogueClient, commentRepository);

    private final UUID orgId = UUID.randomUUID();

    @Test
    void everyVisibleExperience_withZeroWhereNothingIsOpen() {
        UUID withComments = UUID.randomUUID();
        UUID withoutComments = UUID.randomUUID();
        when(catalogueClient.listProjectsForOrg(orgId)).thenReturn(List.of(project(withComments), project(withoutComments)));
        when(commentRepository.countOpenThreads(List.of(withComments, withoutComments))).thenReturn(List.of(count(withComments, 3)));

        List<CountOpenCommentsQuery.OpenCount> counts = query.execute(orgId);

        assertThat(counts).containsExactly(
                new CountOpenCommentsQuery.OpenCount(withComments, 3),
                new CountOpenCommentsQuery.OpenCount(withoutComments, 0));
    }

    @Test
    void onlyAsksForTheExperiencesCatalogueShowsThisCaller() {
        UUID visible = UUID.randomUUID();
        when(catalogueClient.listProjectsForOrg(orgId)).thenReturn(List.of(project(visible)));

        query.execute(orgId);

        verify(commentRepository).countOpenThreads(List.of(visible));
    }

    @Test
    void noVisibleExperiences_returnsEmptyWithoutQuerying() {
        when(catalogueClient.listProjectsForOrg(orgId)).thenReturn(List.of());

        assertThat(query.execute(orgId)).isEmpty();

        verifyNoInteractions(commentRepository);
    }

    @Test
    void noOrgAccess_touchesNothing() {
        when(orgAccessGuard.requireAccess(orgId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(orgId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(catalogueClient, commentRepository);
    }

    private CatalogueProjectDto project(UUID id) {
        return new CatalogueProjectDto(id.toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), Instant.now(), Instant.now());
    }

    private static CommentRepository.OpenThreadCount count(UUID projectId, long open) {
        return new CommentRepository.OpenThreadCount() {
            @Override
            public UUID getProjectId() {
                return projectId;
            }

            @Override
            public long getOpenThreads() {
                return open;
            }
        };
    }
}
