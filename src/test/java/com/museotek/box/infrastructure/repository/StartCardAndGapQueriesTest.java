package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.block.BlockType;
import com.museotek.box.domain.box.Box;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
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

// The native/JPQL queries behind the start-card and gap checks, against real Postgres + Liquibase.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class StartCardAndGapQueriesTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private BoxRepository boxRepository;
    @Autowired
    private BlockRepository blockRepository;

    private final UUID orgId = UUID.randomUUID();

    @Test
    void startCardQueries_findOnlyOtherLiveExperiencesOnTheSameBox() {
        ScanObjectType red = card("Red");
        ScanObjectType blue = card("Blue");
        Project quiz = project("Quiz", red, null);
        Project hunt = project("Hunt", red, null);
        Project story = project("Story", blue, null);
        Project deleted = project("Old", red, Instant.now());
        Project elsewhere = project("Elsewhere", red, null);
        Box hallA = box("Hall A", quiz, hunt, story, deleted);
        box("Hall B", elsewhere);
        em.flush();

        assertThat(boxRepository.findProjectNamesWithStartCard(hallA.getId(), quiz.getId(), red.getId()))
                .containsExactly("Hunt");
        assertThat(boxRepository.findProjectNamesWithStartCard(hallA.getId(), story.getId(), blue.getId()))
                .isEmpty();
        assertThat(boxRepository.findBoxNamesWhereStartCardIsTaken(story.getId(), red.getId()))
                .containsExactly("Hall A");
        assertThat(boxRepository.findBoxNamesWhereStartCardIsTaken(elsewhere.getId(), red.getId()))
                .isEmpty();
    }

    @Test
    void gapQueries_findMediaBlocksWithoutFiles_andScenesThatPlayAVideo() {
        Project project = project("Quiz", card("Red"), null);
        Scene scene = em.getEntityManager()
                .createQuery("select s from Scene s where s.project = :p", Scene.class)
                .setParameter("p", project).getSingleResult();
        Scene screenScene = scene(project, "2", false);
        Scene imageScene = scene(project, "3", false);
        Media video = media(MediaKind.VIDEO);
        Media image = media(MediaKind.IMAGE);

        block(scene, "1", BlockType.TEXT, "{}");
        block(scene, "2", BlockType.IMAGE, "{}");
        block(scene, "3", BlockType.VIDEO, "{\"mediaId\":\"\"}");
        block(screenScene, "4", BlockType.SCREEN, "{\"mediaId\":\"" + video.getId() + "\"}");
        block(imageScene, "5", BlockType.SCREEN, "{\"mediaId\":\"" + image.getId() + "\"}");
        em.flush();

        List<Long> sceneIds = List.of(scene.getId(), screenScene.getId(), imageScene.getId());
        assertThat(blockRepository.findMediaBlockKeysWithoutMedia(sceneIds)).containsExactlyInAnyOrder("2", "3");
        assertThat(blockRepository.findSceneIdsWithVideo(sceneIds)).containsExactlyInAnyOrder(scene.getId(), screenScene.getId());
    }

    private ScanObjectType card(String name) {
        ScanObjectType card = new ScanObjectType();
        card.setOrgId(orgId);
        card.setName(name);
        return em.persist(card);
    }

    // A project with its start scene, which starts with the given card.
    private Project project(String name, ScanObjectType startCard, Instant deletedAt) {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setOrgId(orgId);
        project.setName(name);
        project.setDeletedAt(deletedAt);
        em.persist(project);
        Scene start = scene(project, "1", true);
        start.setInitCardType(startCard);
        return project;
    }

    private Scene scene(Project project, String key, boolean start) {
        Scene scene = new Scene();
        scene.setProject(project);
        scene.setSceneKey(key);
        scene.setName("Scene " + key);
        scene.setPosition(Integer.parseInt(key));
        scene.setStart(start);
        return em.persist(scene);
    }

    private Box box(String name, Project... projects) {
        Box box = new Box();
        box.setOrgId(orgId);
        box.setName(name);
        box.setSerialNumber(UUID.randomUUID().toString());
        box.getAssignedProjects().addAll(List.of(projects));
        return em.persist(box);
    }

    private void block(Scene scene, String key, BlockType type, String content) {
        Block block = new Block();
        block.setScene(scene);
        block.setBlockKey(key);
        block.setType(type);
        block.setPosition(0);
        block.setContent(content);
        em.persist(block);
    }

    private Media media(MediaKind kind) {
        Media media = new Media();
        media.setId(UUID.randomUUID());
        media.setOrgId(orgId);
        media.setKind(kind);
        media.setName("file");
        media.setContentType("application/octet-stream");
        media.setSizeBytes(1);
        media.setStoredFilename(UUID.randomUUID().toString());
        media.setUploadedAt(Instant.now());
        return em.persist(media);
    }
}
