package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.box.BoxStatus;
import com.museotek.box.domain.project.Project;
import com.museotek.box.support.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The status report columns (changeset 0032) and their update queries, against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BoxRepositoryTest {

    private static final Instant T1 = Instant.parse("2026-11-10T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-10T10:00:05Z");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private BoxRepository boxRepository;

    private final UUID orgId = UUID.randomUUID();

    @Test
    void aNewerReport_applies_andAnOlderOneDoesNot() {
        Project project = project();
        Long boxId = box().getId();

        assertThat(boxRepository.applyStatusIfNewer(boxId, BoxStatus.ONLINE, project, "track-3", T2)).isEqualTo(1);
        assertThat(boxRepository.applyStatusIfNewer(boxId, BoxStatus.OFFLINE, null, null, T1)).isZero();
        assertThat(boxRepository.applyStatusIfNewer(boxId, BoxStatus.OFFLINE, null, null, T2)).isZero();

        Box box = em.find(Box.class, boxId);
        assertThat(box.getStatus()).isEqualTo(BoxStatus.ONLINE);
        assertThat(box.getCurrentProject().getId()).isEqualTo(project.getId());
        assertThat(box.getCurrentSceneKey()).isEqualTo("track-3");
        assertThat(box.getStatusAt()).isEqualTo(T2);
    }

    @Test
    void anIdleReport_clearsTheRunningExperience() {
        Project project = project();
        Long boxId = box().getId();
        boxRepository.applyStatusIfNewer(boxId, BoxStatus.ONLINE, project, "track-3", T1);

        boxRepository.applyStatusIfNewer(boxId, BoxStatus.ONLINE, null, null, T2);

        Box box = em.find(Box.class, boxId);
        assertThat(box.getCurrentProject()).isNull();
        assertThat(box.getCurrentSceneKey()).isNull();
    }

    @Test
    void touchLastSeen_setsOnlyLastSeen() {
        Long boxId = box().getId();

        boxRepository.touchLastSeen(boxId, T1);

        Box box = em.find(Box.class, boxId);
        assertThat(box.getLastSeenAt()).isEqualTo(T1);
        assertThat(box.getStatusAt()).isNull();
        assertThat(box.getStatus()).isEqualTo(BoxStatus.OFFLINE);
    }

    private Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(orgId);
        project.setName("Quiz");
        return em.persist(project);
    }

    private Box box() {
        Box box = new Box();
        box.setOrgId(orgId);
        box.setName("Hall A");
        box.setSerialNumber(UUID.randomUUID().toString());
        Box saved = em.persist(box);
        em.flush();
        return saved;
    }
}
