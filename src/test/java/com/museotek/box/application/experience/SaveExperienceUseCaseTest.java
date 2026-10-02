package com.museotek.box.application.experience;

import com.museotek.box.application.experience.ExperienceDocument.BlockDocument;
import com.museotek.box.application.experience.ExperienceDocument.DestinationDocument;
import com.museotek.box.application.experience.ExperienceDocument.FlowDocument;
import com.museotek.box.application.experience.ExperienceDocument.RuleDocument;
import com.museotek.box.application.experience.ExperienceDocument.SceneDocument;
import com.museotek.box.application.experience.ExperienceDocument.TriggerDocument;
import com.museotek.box.application.experience.ExperienceDocument.VariableDocument;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.application.scanobject.ScanObjectSupport;
import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.block.BlockType;
import com.museotek.box.domain.experience.ExperienceFlow;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.experience.ExperienceValidationException;
import com.museotek.box.domain.experience.ExperienceVariable;
import com.museotek.box.domain.experience.VariableKind;
import com.museotek.box.domain.media.Media;
import com.museotek.box.domain.media.MediaKind;
import com.museotek.box.domain.experience.StaleExperienceVersionException;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleDestination;
import com.museotek.box.domain.rule.RuleEffect;
import com.museotek.box.domain.rule.RuleTrigger;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scanobject.ScanObjectTypeNotFoundException;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.catalogue.CatalogueForbiddenException;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.ExperienceFlowRepository;
import com.museotek.box.infrastructure.repository.ExperienceVariableRepository;
import com.museotek.box.infrastructure.repository.MediaRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import com.museotek.box.infrastructure.repository.ScanObjectRepository;
import com.museotek.box.infrastructure.repository.ScanObjectTypeRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SaveExperienceUseCaseTest {

    private final ProjectAccessGuard projectAccessGuard = mock(ProjectAccessGuard.class);
    private final ProjectRepository projectRepository = mock(ProjectRepository.class);
    private final SceneRepository sceneRepository = mock(SceneRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final RuleRepository ruleRepository = mock(RuleRepository.class);
    private final ExperienceFlowRepository experienceFlowRepository = mock(ExperienceFlowRepository.class);
    private final ExperienceVariableRepository experienceVariableRepository = mock(ExperienceVariableRepository.class);
    private final MediaRepository mediaRepository = mock(MediaRepository.class);
    private final ScanObjectRepository scanObjectRepository = mock(ScanObjectRepository.class);
    private final ScanObjectTypeRepository scanObjectTypeRepository = mock(ScanObjectTypeRepository.class);
    private final ScanObjectSupport scanObjectSupport = new ScanObjectSupport(scanObjectRepository, scanObjectTypeRepository);
    private final ExperienceDocumentValidator validator = new ExperienceDocumentValidator();

    private final SaveExperienceUseCase useCase = new SaveExperienceUseCase(
            projectAccessGuard, projectRepository, sceneRepository, blockRepository, ruleRepository,
            experienceFlowRepository, experienceVariableRepository, mediaRepository, scanObjectSupport, validator);

    private static final FlowDocument FLOW = new FlowDocument(1, "{\"details\":{},\"islands\":[]}");

    private final UUID projectId = UUID.randomUUID();
    private final UUID orgId = UUID.randomUUID();

    private final AtomicLong nextSceneId = new AtomicLong(100);
    private final AtomicLong nextBlockId = new AtomicLong(200);
    private final AtomicLong nextRuleId = new AtomicLong(300);

    @Test
    void success_insertsNewSceneBlockAndRule_bumpsVersionAndCounters() {
        Project project = project(0, 1, 1, 1);
        stubAccessAndEmptyProject(project);

        BlockDocument block = new BlockDocument("1", "TEXT", 0, "{}", null);
        RuleEffect reply = new RuleEffect("REPLY", null, null, null, "green", "Correct!", null, null, null, null);
        RuleDocument rule = new RuleDocument("1", 0, new TriggerDocument("SCAN_OTHER", null, null), null,
                List.of(reply), new DestinationDocument("STAY", null));
        SceneDocument scene = new SceneDocument("1", "Scene One", 0, true, null, List.of(block), List.of(rule));

        ExperienceView view = useCase.execute(projectId, 0, document(List.of(scene)));

        assertThat(view.version()).isEqualTo(1);
        assertThat(view.nextSceneSeq()).isEqualTo(2);
        assertThat(view.nextBlockSeq()).isEqualTo(2);
        assertThat(view.nextRuleSeq()).isEqualTo(2);
        assertThat(view.scenes()).hasSize(1);
        assertThat(view.scenes().get(0).blocks()).extracting(Block::getBlockKey).containsExactly("1");
        assertThat(view.scenes().get(0).rules()).extracting(Rule::getRuleKey).containsExactly("1");
        Rule savedRule = view.scenes().get(0).rules().get(0);
        assertThat(savedRule.getTriggerType()).isEqualTo(RuleTrigger.SCAN_OTHER);
        assertThat(savedRule.getEffects()).containsExactly(reply);
        assertThat(savedRule.getDestinationType()).isEqualTo(RuleDestination.STAY);
        assertThat(savedRule.getTargetSceneKey()).isNull();

        ArgumentCaptor<Project> savedProject = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(savedProject.capture());
        assertThat(savedProject.getValue().getDocVersion()).isEqualTo(1);
    }

    @Test
    void success_updatesExistingRowsByKey_reusesRowsAndLeavesCountersUnchanged() {
        Project project = project(5, 2, 2, 1);
        Scene existingScene = scene(10L, "1", "Old Name", true);
        Block existingBlock = block(20L, existingScene, "1", BlockType.TEXT, "{}");
        stubExistingRows(project, List.of(existingScene), List.of(existingBlock), List.of());

        BlockDocument block = new BlockDocument("1", "IMAGE", 0, "{\"a\":1}", null);
        SceneDocument scene = new SceneDocument("1", "New Name", 0, true, null, List.of(block), List.of());

        ExperienceView view = useCase.execute(projectId, 5, document(List.of(scene)));

        assertThat(view.scenes()).hasSize(1);
        assertThat(view.scenes().get(0).scene().getId()).isEqualTo(10L);
        assertThat(view.scenes().get(0).scene().getName()).isEqualTo("New Name");
        assertThat(view.scenes().get(0).blocks().get(0).getId()).isEqualTo(20L);
        assertThat(view.scenes().get(0).blocks().get(0).getType()).isEqualTo(BlockType.IMAGE);
        // both keys already existed - the write-wide counters must not move backwards or forwards
        assertThat(view.nextSceneSeq()).isEqualTo(2);
        assertThat(view.nextBlockSeq()).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void success_deletesRowsMissingFromDocument_childrenBeforeParent() {
        Project project = project(0, 3, 3, 1);
        Scene keptScene = scene(10L, "1", "Kept", true);
        Scene removedScene = scene(11L, "2", "Removed", false);
        Block keptBlock = block(20L, keptScene, "1", BlockType.TEXT, "{}");
        Block removedBlock = block(21L, removedScene, "2", BlockType.TEXT, "{}");
        stubExistingRows(project, List.of(keptScene, removedScene), List.of(keptBlock, removedBlock), List.of());

        BlockDocument block = new BlockDocument("1", "TEXT", 0, "{}", null);
        SceneDocument scene = new SceneDocument("1", "Kept", 0, true, null, List.of(block), List.of());

        useCase.execute(projectId, 0, document(List.of(scene)));

        ArgumentCaptor<List<Scene>> sceneDeleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(sceneRepository).deleteAll(sceneDeleteCaptor.capture());
        assertThat(sceneDeleteCaptor.getValue()).extracting(Scene::getId).containsExactly(11L);

        ArgumentCaptor<List<Block>> blockDeleteCaptor = ArgumentCaptor.forClass(List.class);
        verify(blockRepository).deleteAll(blockDeleteCaptor.capture());
        assertThat(blockDeleteCaptor.getValue()).extracting(Block::getId).containsExactly(21L);
    }

    @Test
    void staleVersion_throwsWithoutTouchingSceneBlockOrRuleRepositories() {
        Project project = project(5, 1, 1, 1);
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject());
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ExperienceDocument document = document(List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 3, document))
                .isInstanceOf(StaleExperienceVersionException.class)
                .satisfies(e -> {
                    StaleExperienceVersionException stale = (StaleExperienceVersionException) e;
                    assertThat(stale.getProjectId()).isEqualTo(projectId);
                });

        verifyNoInteractions(sceneRepository, blockRepository, ruleRepository);
    }

    @Test
    void graphValidationFailure_throwsWithoutSavingAnything() {
        Project project = project(0, 1, 1, 1);
        stubAccessAndEmptyProject(project);

        // two start scenes - fails validateGraph's "exactly one" rule
        SceneDocument first = new SceneDocument("1", "A", 0, true, null, List.of(), List.of());
        SceneDocument second = new SceneDocument("2", "B", 1, true, null, List.of(), List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 0, document(List.of(first, second))))
                .isInstanceOf(ExperienceValidationException.class)
                .satisfies(e -> assertThat(((ExperienceValidationException) e).getErrors())
                        .anyMatch(msg -> msg.contains("exactly one scene must have is_start")));

        assertNoWritesHappened();
    }

    @Test
    void malformedBlockType_throwsExperienceValidationExceptionNotIllegalArgument() {
        Project project = project(0, 1, 1, 1);
        stubAccessAndEmptyProject(project);

        BlockDocument badBlock = new BlockDocument("1", "NOT_A_TYPE", 0, "{}", null);
        SceneDocument scene = new SceneDocument("1", "A", 0, true, null, List.of(badBlock), List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 0, document(List.of(scene))))
                .isInstanceOf(ExperienceValidationException.class)
                .satisfies(e -> assertThat(((ExperienceValidationException) e).getErrors())
                        .anyMatch(msg -> msg.contains("NOT_A_TYPE") && msg.contains("not a valid block type")));

        assertNoWritesHappened();
    }

    @Test
    void unknownInitCardType_propagatesScanObjectTypeNotFoundException() {
        Project project = project(0, 1, 1, 1);
        stubAccessAndEmptyProject(project);
        when(scanObjectTypeRepository.findByIdAndOrgId(99L, orgId)).thenReturn(Optional.empty());

        SceneDocument scene = new SceneDocument("1", "A", 0, true, 99L, List.of(), List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 0, document(List.of(scene))))
                .isInstanceOf(ScanObjectTypeNotFoundException.class);

        verify(sceneRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void projectAccessDenied_neverTouchesAnyRepository() {
        when(projectAccessGuard.requireAccess(projectId)).thenThrow(new CatalogueForbiddenException("not a member"));

        ExperienceDocument document = document(List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 0, document))
                .isInstanceOf(CatalogueForbiddenException.class);

        verifyNoInteractions(projectRepository, sceneRepository, blockRepository, ruleRepository);
    }


    @Test
    void success_savesFlowAndVariablesWithTheGraph() {
        Project project = project(0, 1, 1, 1);
        stubAccessAndEmptyProject(project);
        SceneDocument scene = new SceneDocument("1", "Start", 0, true, null, List.of(), List.of());
        ExperienceDocument document = new ExperienceDocument("DISPLAY", FLOW,
                List.of(new VariableDocument("wrong", "NUMBER", "0"), new VariableDocument("correct", "NUMBER", "0")),
                List.of(scene));

        ExperienceView view = useCase.execute(projectId, 0, document);

        assertThat(view.flow().getProjectId()).isEqualTo(projectId);
        assertThat(view.flow().getSchemaVersion()).isEqualTo(1);
        assertThat(view.flow().getFlow()).isEqualTo("{\"details\":{},\"islands\":[]}");
        assertThat(view.variables()).extracting(ExperienceVariable::getVariableKey).containsExactly("correct", "wrong");
        assertThat(view.variables()).allSatisfy(v -> assertThat(v.getProject()).isSameAs(project));
    }

    @Test
    void success_updatesExistingFlowAndVariablesInPlace_deletesMissingVariables() {
        Project project = project(2, 1, 1, 1);
        stubAccessAndEmptyProject(project);
        ExperienceFlow existingFlow = new ExperienceFlow();
        existingFlow.setProjectId(projectId);
        existingFlow.setSchemaVersion(1);
        existingFlow.setFlow("{\"old\":true}");
        existingFlow.setOutput(ExperienceOutput.DISPLAY);
        when(experienceFlowRepository.findById(projectId)).thenReturn(Optional.of(existingFlow));
        ExperienceVariable kept = variable(1L, project, "correct", VariableKind.NUMBER, "0");
        ExperienceVariable dropped = variable(2L, project, "found", VariableKind.NUMBER, "0");
        when(experienceVariableRepository.findByProjectIdOrderByVariableKeyAsc(projectId)).thenReturn(List.of(kept, dropped));
        SceneDocument scene = new SceneDocument("1", "Start", 0, true, null, List.of(), List.of());
        ExperienceDocument document = new ExperienceDocument("DISPLAY", FLOW,
                List.of(new VariableDocument("correct", "NUMBER", "5")), List.of(scene));

        ExperienceView view = useCase.execute(projectId, 2, document);

        assertThat(view.flow()).isSameAs(existingFlow);
        assertThat(existingFlow.getFlow()).isEqualTo("{\"details\":{},\"islands\":[]}");
        assertThat(view.variables()).containsExactly(kept);
        assertThat(kept.getInitialValue()).isEqualTo("5");
        ArgumentCaptor<List<ExperienceVariable>> deleted = ArgumentCaptor.forClass(List.class);
        verify(experienceVariableRepository).deleteAll(deleted.capture());
        assertThat(deleted.getValue()).containsExactly(dropped);
    }

    @Test
    void missingFlow_throwsWithoutSavingAnything() {
        stubAccessAndEmptyProject(project(0, 1, 1, 1));
        SceneDocument scene = new SceneDocument("1", "Start", 0, true, null, List.of(), List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 0, new ExperienceDocument("DISPLAY", null, List.of(), List.of(scene))))
                .isInstanceOf(ExperienceValidationException.class);

        assertNoWritesHappened();
    }


    @Test
    void firstSave_storesOutput() {
        stubAccessAndEmptyProject(project(0, 1, 1, 1));
        SceneDocument scene = new SceneDocument("1", "Start", 0, true, null, List.of(), List.of());

        ExperienceView view = useCase.execute(projectId, 0, new ExperienceDocument("BOX", FLOW, List.of(), List.of(scene)));

        assertThat(view.flow().getOutput()).isEqualTo(ExperienceOutput.BOX);
    }

    @Test
    void changingOutputAfterCreation_throwsWithoutSavingAnything() {
        stubAccessAndEmptyProject(project(1, 2, 1, 1));
        ExperienceFlow existingFlow = new ExperienceFlow();
        existingFlow.setProjectId(projectId);
        existingFlow.setSchemaVersion(1);
        existingFlow.setFlow("{}");
        existingFlow.setOutput(ExperienceOutput.DISPLAY);
        when(experienceFlowRepository.findById(projectId)).thenReturn(Optional.of(existingFlow));
        SceneDocument scene = new SceneDocument("2", "Start", 0, true, null, List.of(), List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 1, new ExperienceDocument("BOX", FLOW, List.of(), List.of(scene))))
                .isInstanceOf(ExperienceValidationException.class)
                .hasMessageContaining("output can't be changed");

        assertNoWritesHappened();
        assertThat(existingFlow.getOutput()).isEqualTo(ExperienceOutput.DISPLAY);
    }


    @Test
    void mediaReference_isLookedUpInTheProjectsOrgAndSaved() {
        stubAccessAndEmptyProject(project(0, 1, 1, 1));
        UUID image = UUID.randomUUID();
        Media media = new Media();
        media.setId(image);
        media.setKind(MediaKind.IMAGE);
        when(mediaRepository.findByOrgIdAndIdIn(eq(orgId), eq(java.util.Set.of(image)))).thenReturn(List.of(media));
        BlockDocument block = new BlockDocument("1", "IMAGE", 0, "{\"mediaId\":\"" + image + "\"}", image.toString());
        SceneDocument scene = new SceneDocument("1", "Start", 0, true, null, List.of(block), List.of());

        ExperienceView view = useCase.execute(projectId, 0, document(List.of(scene)));

        assertThat(view.scenes().get(0).blocks().get(0).getContent()).contains(image.toString());
    }

    @Test
    void mediaFromAnotherOrg_isRefusedWithoutSavingAnything() {
        stubAccessAndEmptyProject(project(0, 1, 1, 1));
        UUID otherOrgsImage = UUID.randomUUID();
        when(mediaRepository.findByOrgIdAndIdIn(eq(orgId), any())).thenReturn(List.of()); // not found in this org
        BlockDocument block = new BlockDocument("1", "IMAGE", 0, "{}", otherOrgsImage.toString());
        SceneDocument scene = new SceneDocument("1", "Start", 0, true, null, List.of(block), List.of());

        assertThatThrownBy(() -> useCase.execute(projectId, 0, document(List.of(scene))))
                .isInstanceOf(ExperienceValidationException.class)
                .hasMessageContaining("does not exist in this organisation's media library");

        assertNoWritesHappened();
    }

    private ExperienceDocument document(List<SceneDocument> scenes) {
        return new ExperienceDocument("DISPLAY", FLOW, List.of(), scenes);
    }

    private ExperienceVariable variable(Long id, Project project, String key, VariableKind kind, String initial) {
        ExperienceVariable variable = new ExperienceVariable();
        variable.setId(id);
        variable.setProject(project);
        variable.setVariableKey(key);
        variable.setKind(kind);
        variable.setInitialValue(initial);
        return variable;
    }

    // reading existing rows to build the validation context is legitimate (and always happens
    // before validation runs) - what must NOT happen on a rejected write is any actual mutation.
    private void assertNoWritesHappened() {
        verify(sceneRepository, org.mockito.Mockito.never()).save(any());
        verify(sceneRepository, org.mockito.Mockito.never()).deleteAll(any());
        verify(blockRepository, org.mockito.Mockito.never()).save(any());
        verify(blockRepository, org.mockito.Mockito.never()).deleteAll(any());
        verify(ruleRepository, org.mockito.Mockito.never()).save(any());
        verify(ruleRepository, org.mockito.Mockito.never()).deleteAll(any());
        verify(projectRepository, org.mockito.Mockito.never()).save(any());
        verify(experienceFlowRepository, org.mockito.Mockito.never()).save(any());
        verify(experienceVariableRepository, org.mockito.Mockito.never()).save(any());
        verify(experienceVariableRepository, org.mockito.Mockito.never()).deleteAll(any());
    }

    private void stubAccessAndEmptyProject(Project project) {
        stubExistingRows(project, List.of(), List.of(), List.of());
    }

    private void stubExistingRows(Project project, List<Scene> scenes, List<Block> blocks, List<Rule> rules) {
        when(projectAccessGuard.requireAccess(projectId)).thenReturn(catalogueProject());
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(sceneRepository.findByProjectIdOrderByPositionAsc(projectId)).thenReturn(scenes);
        when(blockRepository.findBySceneIdInOrderByPositionAsc(anyCollection())).thenReturn(blocks);
        when(ruleRepository.findBySceneIdInOrderByPositionAsc(anyCollection())).thenReturn(rules);
        when(projectRepository.save(any(Project.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(experienceFlowRepository.save(any(ExperienceFlow.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(experienceVariableRepository.save(any(ExperienceVariable.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sceneRepository.save(any(Scene.class))).thenAnswer(invocation -> {
            Scene scene = invocation.getArgument(0);
            if (scene.getId() == null) {
                scene.setId(nextSceneId.getAndIncrement());
            }
            return scene;
        });
        when(blockRepository.save(any(Block.class))).thenAnswer(invocation -> {
            Block block = invocation.getArgument(0);
            if (block.getId() == null) {
                block.setId(nextBlockId.getAndIncrement());
            }
            return block;
        });
        when(ruleRepository.save(any(Rule.class))).thenAnswer(invocation -> {
            Rule rule = invocation.getArgument(0);
            if (rule.getId() == null) {
                rule.setId(nextRuleId.getAndIncrement());
            }
            return rule;
        });
    }

    private Project project(int docVersion, int nextSceneSeq, int nextBlockSeq, int nextRuleSeq) {
        Project project = new Project();
        project.setId(projectId);
        project.setOrgId(orgId);
        project.setDocVersion(docVersion);
        project.setNextSceneSeq(nextSceneSeq);
        project.setNextBlockSeq(nextBlockSeq);
        project.setNextRuleSeq(nextRuleSeq);
        return project;
    }

    private Scene scene(Long id, String sceneKey, String name, boolean isStart) {
        Scene scene = new Scene();
        scene.setId(id);
        scene.setSceneKey(sceneKey);
        scene.setName(name);
        scene.setPosition(0);
        scene.setStart(isStart);
        return scene;
    }

    private Block block(Long id, Scene scene, String blockKey, BlockType type, String content) {
        Block block = new Block();
        block.setId(id);
        block.setScene(scene);
        block.setBlockKey(blockKey);
        block.setType(type);
        block.setPosition(0);
        block.setContent(content);
        return block;
    }

    private CatalogueProjectDto catalogueProject() {
        return new CatalogueProjectDto(
                projectId.toString(), orgId.toString(), "Project", "project-slug", "ACTIVE", null, null, null);
    }
}
