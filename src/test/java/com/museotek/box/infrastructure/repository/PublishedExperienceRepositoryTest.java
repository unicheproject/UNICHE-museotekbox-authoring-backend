package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.box.Box;
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
    void startCardQueries_lookAtTheLatestCopyOfOtherLiveExperiencesOnTheSameBox() {
        Project quiz = project("Quiz");
        Project hunt = project("Hunt");
        Project story = project("Story");
        Project deleted = project("Old");
        deleted.setDeletedAt(Instant.now());
        Project draftOnly = project("Draft only");
        Project elsewhere = project("Elsewhere");
        em.persist(copy(quiz, 1, RED, List.of()));
        em.persist(copy(hunt, 1, BLUE, List.of()));
        em.persist(copy(hunt, 2, RED, List.of()));    // latest: now starts with red
        em.persist(copy(story, 1, RED, List.of()));
        em.persist(copy(story, 2, BLUE, List.of()));  // latest: moved away from red
        em.persist(copy(deleted, 1, RED, List.of()));
        em.persist(copy(elsewhere, 1, RED, List.of()));
        Box hallA = box("Hall A", quiz, hunt, story, deleted, draftOnly);
        box("Hall B", elsewhere);
        em.flush();

        assertThat(publishedExperienceRepository.findProjectNamesOnBoxWithStartCard(hallA.getId(), quiz.getId(), RED))
                .containsExactly("Hunt");
        assertThat(publishedExperienceRepository.findProjectNamesOnBoxWithStartCard(hallA.getId(), quiz.getId(), 99L))
                .isEmpty();
        assertThat(publishedExperienceRepository.findBoxNamesWhereStartCardIsTaken(story.getId(), RED))
                .containsExactly("Hall A");
        assertThat(publishedExperienceRepository.findBoxNamesWhereStartCardIsTaken(elsewhere.getId(), RED))
                .isEmpty();
    }

    @Test
    void inUseQueries_lookOnlyAtTheLatestCopy() {
        Project project = project("Quiz");
        String oldMedia = UUID.randomUUID().toString();
        String newMedia = UUID.randomUUID().toString();
        em.persist(copy(project, 1, 7L, List.of(oldMedia)));
        em.persist(copy(project, 2, 9L, List.of(newMedia)));
        em.flush();

        assertThat(publishedExperienceRepository.latestCopyUsesMedia(newMedia)).isTrue();
        assertThat(publishedExperienceRepository.latestCopyUsesMedia(oldMedia)).isFalse();
        assertThat(publishedExperienceRepository.latestCopyUsesCardType(9L)).isTrue();
        assertThat(publishedExperienceRepository.latestCopyUsesCardType(8L)).isTrue();
        assertThat(publishedExperienceRepository.latestCopyUsesCardType(7L)).isFalse();
    }

    @Test
    void latestCopyStartsWith_looksOnlyAtTheLatestCopy() {
        Project project = project("Quiz");
        em.persist(copy(project, 1, RED, List.of()));
        em.persist(copy(project, 2, BLUE, List.of()));
        em.flush();

        assertThat(publishedExperienceRepository.latestCopyStartsWith(BLUE)).isTrue();
        assertThat(publishedExperienceRepository.latestCopyStartsWith(RED)).isFalse();
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

    private static final Long RED = 7L;
    private static final Long BLUE = 8L;
    private final UUID orgId = UUID.randomUUID();

    private Project project() {
        return project("Quiz");
    }

    private Project project(String name) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(orgId);
        project.setName(name);
        return em.persist(project);
    }

    private Box box(String name, Project... projects) {
        Box box = new Box();
        box.setOrgId(orgId);
        box.setName(name);
        box.setSerialNumber(UUID.randomUUID().toString());
        box.getAssignedProjects().addAll(List.of(projects));
        return em.persist(box);
    }

    private static PublishedExperience copy(Project project, int version, List<String> mediaIds) {
        return copy(project, version, RED, mediaIds);
    }

    private static PublishedExperience copy(Project project, int version, Long startCardTypeId, List<String> mediaIds) {
        return new PublishedExperience(project, version, project.getName(), ExperienceOutput.DISPLAY, startCardTypeId,
                "{\"contentSchemaVersion\":1,\"scenes\":[]}", mediaIds, List.of(startCardTypeId, 8L),
                Instant.parse("2026-11-03T10:00:00Z"), "someone");
    }
}
