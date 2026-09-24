package com.museotek.box.application.experience;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.block.BlockType;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleAction;
import com.museotek.box.domain.rule.RuleEventType;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.catalogue.CatalogueNotFoundException;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GetExperienceQueryTest {

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final SceneRepository sceneRepository = mock(SceneRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final RuleRepository ruleRepository = mock(RuleRepository.class);
    private final GetExperienceQuery query = new GetExperienceQuery(
            projectAccessGuard, projectRepository, sceneRepository, blockRepository, ruleRepository);

    private final UUID projectId = UUID.randomUUID();

    @Test
    void groupsEachScenesOwnBlocksAndRulesUnderIt() {
        stubProject(3, 4, 5, 6);
        Scene first = scene(10L, "s1", 0);
        Scene second = scene(20L, "s2", 1);
        when(sceneRepository.findByProjectIdOrderByPositionAsc(projectId)).thenReturn(List.of(first, second));
        when(blockRepository.findBySceneIdInOrderByPositionAsc(anyCollection()))
                .thenReturn(List.of(block(first, "b1"), block(second, "b2"), block(first, "b3")));
        when(ruleRepository.findBySceneIdInOrderByPositionAsc(anyCollection()))
                .thenReturn(List.of(rule(second, "r1")));

        ExperienceView view = query.execute(projectId);

        assertThat(view.projectId()).isEqualTo(projectId);
        assertThat(view.version()).isEqualTo(3);
        assertThat(view.nextSceneSeq()).isEqualTo(4);
        assertThat(view.nextBlockSeq()).isEqualTo(5);
        assertThat(view.nextRuleSeq()).isEqualTo(6);
        assertThat(view.scenes()).hasSize(2);

        ExperienceView.SceneView firstView = view.scenes().get(0);
        assertThat(firstView.scene()).isSameAs(first);
        assertThat(firstView.blocks()).extracting(Block::getBlockKey).containsExactly("b1", "b3");
        assertThat(firstView.rules()).isEmpty();

        ExperienceView.SceneView secondView = view.scenes().get(1);
        assertThat(secondView.blocks()).extracting(Block::getBlockKey).containsExactly("b2");
        assertThat(secondView.rules()).extracting(Rule::getRuleKey).containsExactly("r1");
    }

    @Test
    void projectWithNoScenes_returnsEmptyDocumentWithoutQueryingBlocksOrRules() {
        stubProject(0, 1, 1, 1);
        when(sceneRepository.findByProjectIdOrderByPositionAsc(projectId)).thenReturn(List.of());

        ExperienceView view = query.execute(projectId);

        assertThat(view.version()).isZero();
        assertThat(view.nextSceneSeq()).isEqualTo(1);
        assertThat(view.scenes()).isEmpty();
        verifyNoInteractions(blockRepository, ruleRepository);
    }

    @Test
    void projectAccessDenied_neverTouchesRepositories() {
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueNotFoundException("gone"));

        assertThatThrownBy(() -> query.execute(projectId)).isInstanceOf(CatalogueNotFoundException.class);

        verifyNoInteractions(projectRepository, sceneRepository, blockRepository, ruleRepository);
    }

    private void stubProject(int version, int nextSceneSeq, int nextBlockSeq, int nextRuleSeq) {
        Project project = new Project();
        project.setDocVersion(version);
        project.setNextSceneSeq(nextSceneSeq);
        project.setNextBlockSeq(nextBlockSeq);
        project.setNextRuleSeq(nextRuleSeq);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
    }

    private Scene scene(Long id, String sceneKey, int position) {
        Scene scene = new Scene();
        scene.setId(id);
        scene.setSceneKey(sceneKey);
        scene.setPosition(position);
        return scene;
    }

    private Block block(Scene scene, String blockKey) {
        Block block = new Block();
        block.setScene(scene);
        block.setBlockKey(blockKey);
        block.setType(BlockType.TEXT);
        block.setContent("{}");
        return block;
    }

    private Rule rule(Scene scene, String ruleKey) {
        Rule rule = new Rule();
        rule.setScene(scene);
        rule.setRuleKey(ruleKey);
        rule.setEventType(RuleEventType.TAG_SCANNED);
        rule.setAction(RuleAction.GO_TO_SCENE);
        return rule;
    }
}
