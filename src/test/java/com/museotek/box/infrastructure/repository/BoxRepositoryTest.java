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

// The Box's status columns as its socket and state messages set them, against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class BoxRepositoryTest {

    private static final Instant T1 = Instant.parse("2026-11-18T10:00:00Z");
    private static final Instant T2 = Instant.parse("2026-11-18T10:00:05Z");

    @Autowired
    private TestEntityManager em;
    @Autowired
    private BoxRepository boxRepository;

    private final UUID orgId = UUID.randomUUID();

    @Test
    void setPresence_setsTheStatusAndTime_andClearsWhatItPlays() {
        Long boxId = box().getId();
        Project project = project();
        boxRepository.setPlaying(boxId, project, "track-3");

        assertThat(boxRepository.setPresence(boxId, BoxStatus.ONLINE, T1)).isEqualTo(1);

        Box box = em.find(Box.class, boxId);
        assertThat(box.getStatus()).isEqualTo(BoxStatus.ONLINE);
        assertThat(box.getStatusAt()).isEqualTo(T1);
        assertThat(box.getLastSeenAt()).isEqualTo(T1);
        assertThat(box.getCurrentProject()).isNull();
        assertThat(box.getCurrentSceneKey()).isNull();
    }

    @Test
    void setPlaying_setsOnlyWhatItPlays() {
        Long boxId = box().getId();
        boxRepository.setPresence(boxId, BoxStatus.ONLINE, T1);
        Project project = project();

        boxRepository.setPlaying(boxId, project, "track-3");

        Box box = em.find(Box.class, boxId);
        assertThat(box.getCurrentProject().getId()).isEqualTo(project.getId());
        assertThat(box.getCurrentSceneKey()).isEqualTo("track-3");
        assertThat(box.getStatus()).isEqualTo(BoxStatus.ONLINE);
        assertThat(box.getStatusAt()).isEqualTo(T1);
    }

    @Test
    void markAllOffline_touchesOnlyBoxesThatAreNotOfflineYet() {
        Long online = box().getId();
        Long offline = box().getId();
        boxRepository.setPresence(online, BoxStatus.ONLINE, T1);
        boxRepository.setPresence(offline, BoxStatus.OFFLINE, T1);

        int changed = boxRepository.markAllOffline(BoxStatus.OFFLINE, T2);

        assertThat(changed).isGreaterThanOrEqualTo(1);
        assertThat(em.find(Box.class, online).getStatus()).isEqualTo(BoxStatus.OFFLINE);
        assertThat(em.find(Box.class, online).getStatusAt()).isEqualTo(T2);
        assertThat(em.find(Box.class, offline).getStatusAt()).isEqualTo(T1);
    }

    @Test
    void findIdsByAssignedProject_findsTheBoxesThatHaveIt() {
        Project project = project();
        Box hallA = box();
        Box hallB = box();
        box();
        hallA.getAssignedProjects().add(project);
        hallB.getAssignedProjects().add(project);
        em.flush();

        assertThat(boxRepository.findIdsByAssignedProject(project.getId()))
                .containsExactlyInAnyOrder(hallA.getId(), hallB.getId());
    }

    private Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(orgId);
        project.setName("Quiz");
        Project saved = em.persist(project);
        em.flush();
        return saved;
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
