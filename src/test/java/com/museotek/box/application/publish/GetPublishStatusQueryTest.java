package com.museotek.box.application.publish;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.ExperienceNotPublishedException;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.PublishedExperienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetPublishStatusQueryTest {

    private static final Instant PUBLISHED = Instant.parse("2026-11-03T10:00:00Z");

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final PublishedExperienceRepository publishedExperienceRepository = mock(PublishedExperienceRepository.class);
    private final PublishWarnings publishWarnings = mock(PublishWarnings.class);
    private final GetPublishStatusQuery query = new GetPublishStatusQuery(
            projectAccessGuard, projectRepository, publishedExperienceRepository, publishWarnings);

    private final UUID orgId = UUID.randomUUID();
    private final UUID projectId = UUID.randomUUID();

    @BeforeEach
    void projectInOrg() {
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(new CatalogueProjectDto(
                projectId.toString(), orgId.toString(), "Wing", "wing", "ACTIVE",
                new CatalogueProjectDto.CatalogueToolDto("museotek-box"), PUBLISHED, PUBLISHED));
    }

    @Test
    void draftSavedAfterPublishing_hasUnpublishedChanges() {
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId))
                .thenReturn(Optional.of(copyOf(project(4), 2)));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(6)));

        assertThat(query.execute(projectId)).isEqualTo(new PublishStatus(2, 4, PUBLISHED, true, List.of()));
    }

    @Test
    void draftUnchangedSincePublishing_hasNoUnpublishedChanges() {
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId))
                .thenReturn(Optional.of(copyOf(project(4), 2)));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(4)));

        assertThat(query.execute(projectId).hasUnpublishedChanges()).isFalse();
    }

    @Test
    void orgWarnings_areReturned() {
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId))
                .thenReturn(Optional.of(copyOf(project(4), 2)));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project(4)));
        when(publishWarnings.find(orgId)).thenReturn(List.of("no Exit card"));

        assertThat(query.execute(projectId).warnings()).containsExactly("no Exit card");
    }

    @Test
    void neverPublished_isNotPublished() {
        when(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(projectId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> query.execute(projectId)).isInstanceOf(ExperienceNotPublishedException.class);
    }

    @Test
    void projectAccessDenied_neverReadsTheCopies() {
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueForbiddenException("not a member"));

        assertThatThrownBy(() -> query.execute(projectId)).isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(publishedExperienceRepository, projectRepository, publishWarnings);
    }

    private Project project(int docVersion) {
        Project project = new Project();
        project.setId(projectId);
        project.setDocVersion(docVersion);
        return project;
    }

    private static PublishedExperience copyOf(Project draft, int version) {
        return new PublishedExperience(draft, version, "Wing", ExperienceOutput.DISPLAY, 7L, "{}", List.of(), List.of(7L),
                PUBLISHED, "someone");
    }
}
