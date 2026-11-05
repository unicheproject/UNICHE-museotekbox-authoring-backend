package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.publish.PublishedExperience;
import com.museotek.box.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The published_experiences mapping (changeset 0029, jsonb columns) and its queries, against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class PublishedExperienceRepositoryTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private PublishedExperienceRepository publishedExperienceRepository;
    @Autowired
    private ProjectRepository projectRepository;

    @Test
    void latestCopy_isTheHighestVersion_withItsListsReadBack() {
        Project project = project();
        String media = UUID.randomUUID().toString();
        em.persist(copy(project, 1, List.of()));
        em.persist(copy(project, 2, List.of(media)));
        em.persist(copy(project(), 5, List.of()));
        em.flush();
        em.clear();

        PublishedExperience latest = publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(project.getId()).orElseThrow();

        assertThat(latest.getVersion()).isEqualTo(2);
        assertThat(latest.getMediaIds()).containsExactly(media);
        assertThat(latest.getCardTypeIds()).containsExactly(7L, 8L);
        assertThat(latest.getContent()).contains("\"contentSchemaVersion\"");
    }

    @Test
    void neverPublished_hasNoLatestCopy() {
        assertThat(publishedExperienceRepository.findTopByProjectIdOrderByVersionDesc(project().getId())).isEmpty();
    }

    @Test
    void findByIdForUpdate_findsTheProject() {
        Project project = project();
        em.flush();

        assertThat(projectRepository.findByIdForUpdate(project.getId())).contains(project);
    }

    private Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(UUID.randomUUID());
        project.setName("Quiz");
        return em.persist(project);
    }

    private static PublishedExperience copy(Project project, int version, List<String> mediaIds) {
        return new PublishedExperience(project, version, "Quiz", ExperienceOutput.DISPLAY, 7L,
                "{\"contentSchemaVersion\":1,\"scenes\":[]}", mediaIds, List.of(7L, 8L),
                Instant.parse("2026-11-03T10:00:00Z"), "someone");
    }
}
