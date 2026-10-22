package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.template.ExperienceTemplate;
import com.museotek.box.domain.template.ExperienceTemplateType;
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

// The seeded global templates and the "global + own" queries, against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ExperienceTemplateRepositoryTest {

    private static final UUID GLOBAL_QUIZ_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private ExperienceTemplateRepository repository;

    private final UUID orgId = UUID.randomUUID();
    private final UUID otherOrgId = UUID.randomUUID();

    @Test
    void liquibaseSeedsOneGlobalTemplatePerType() {
        List<ExperienceTemplate> globals = repository.findVisibleTo(UUID.randomUUID());

        assertThat(globals).allMatch(ExperienceTemplate::isGlobal);
        assertThat(globals).extracting(ExperienceTemplate::getType)
                .containsExactlyInAnyOrder(ExperienceTemplateType.values());
        assertThat(globals).extracting(ExperienceTemplate::getDefaults).containsOnly("{}");
    }

    @Test
    void anOrgSeesTheGlobalTemplatesAndItsOwnButNotAnotherOrgs() {
        ExperienceTemplate own = persist(orgId, ExperienceTemplateType.QUIZ, "School Quiz");
        ExperienceTemplate others = persist(otherOrgId, ExperienceTemplateType.QUIZ, "Their Quiz");

        List<ExperienceTemplate> visible = repository.findVisibleTo(orgId);

        assertThat(visible).extracting(ExperienceTemplate::getId)
                .contains(own.getId(), GLOBAL_QUIZ_ID)
                .doesNotContain(others.getId());
        assertThat(visible).hasSize(ExperienceTemplateType.values().length + 1);
    }

    @Test
    void theTypeFilterKeepsOnlyThatType() {
        persist(orgId, ExperienceTemplateType.QUIZ, "School Quiz");
        persist(orgId, ExperienceTemplateType.TREASURE_HUNT, "Garden hunt");

        List<ExperienceTemplate> quizzes = repository.findVisibleToByType(orgId, ExperienceTemplateType.QUIZ);

        assertThat(quizzes).extracting(ExperienceTemplate::getName).containsExactlyInAnyOrder("Quiz", "School Quiz");
    }

    @Test
    void findByIdFindsGlobalAndOwnButNotAnotherOrgs() {
        ExperienceTemplate others = persist(otherOrgId, ExperienceTemplateType.QUIZ, "Their Quiz");

        assertThat(repository.findByIdVisibleTo(GLOBAL_QUIZ_ID, orgId)).isPresent();
        assertThat(repository.findByIdVisibleTo(others.getId(), orgId)).isEmpty();
        assertThat(repository.findByIdVisibleTo(others.getId(), otherOrgId)).isPresent();
    }

    @Test
    void defaultsRoundTripAsJson() {
        ExperienceTemplate saved = persist(orgId, ExperienceTemplateType.QUIZ, "School Quiz");
        em.clear();

        ExperienceTemplate loaded = repository.findById(saved.getId()).orElseThrow();

        assertThat(loaded.getDefaults()).contains("\"questions\"").contains("5");
    }

    private ExperienceTemplate persist(UUID org, ExperienceTemplateType type, String name) {
        ExperienceTemplate template = new ExperienceTemplate();
        template.setId(UUID.randomUUID());
        template.setOrgId(org);
        template.setType(type);
        template.setName(name);
        template.setDefaults("{\"questions\": 5}");
        template.setCreatedBy("subject-1");
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(Instant.now());
        return em.persistAndFlush(template);
    }
}
