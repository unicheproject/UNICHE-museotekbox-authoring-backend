package com.museotek.box.application.experience;

import com.museotek.box.application.experience.ExperienceDocument.BlockDocument;
import com.museotek.box.application.experience.ExperienceDocument.RuleDocument;
import com.museotek.box.application.experience.ExperienceDocument.SceneDocument;
import com.museotek.box.application.experience.ExperienceDocument.VariableDocument;
import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.application.scanobject.ScanObjectSupport;
import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.block.BlockType;
import com.museotek.box.domain.experience.ExperienceFlow;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.experience.ExperienceVariable;
import com.museotek.box.domain.experience.VariableKind;
import com.museotek.box.domain.experience.ExperienceValidationException;
import com.museotek.box.domain.experience.StaleExperienceVersionException;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleAction;
import com.museotek.box.domain.rule.RuleEventType;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.catalogue.CatalogueProjectDto;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.ExperienceFlowRepository;
import com.museotek.box.infrastructure.repository.ExperienceVariableRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The write algorithm's steps 4-5 (see the document-model proposal doc): diff + apply the
 * incoming document against the project's current rows in one transaction, then bump the
 * version. Steps 1-3 (validate keys / graph / freshness) are delegated to
 * {@link ExperienceDocumentValidator}; this class adds one more validation pass of its own
 * (block type / rule event type / rule action well-formedness) before applying anything,
 * since a bad enum value would otherwise surface as an unhandled exception mid-apply.
 */
@Service
public class SaveExperienceUseCase {

    private final ProjectAccessGuard projectAccessGuard;
    private final ProjectRepository projectRepository;
    private final SceneRepository sceneRepository;
    private final BlockRepository blockRepository;
    private final RuleRepository ruleRepository;
    private final ExperienceFlowRepository experienceFlowRepository;
    private final ExperienceVariableRepository experienceVariableRepository;
    private final ScanObjectSupport scanObjectSupport;
    private final ExperienceDocumentValidator validator;

    public SaveExperienceUseCase(
            ProjectAccessGuard projectAccessGuard,
            ProjectRepository projectRepository,
            SceneRepository sceneRepository,
            BlockRepository blockRepository,
            RuleRepository ruleRepository,
            ExperienceFlowRepository experienceFlowRepository,
            ExperienceVariableRepository experienceVariableRepository,
            ScanObjectSupport scanObjectSupport,
            ExperienceDocumentValidator validator
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.projectRepository = projectRepository;
        this.sceneRepository = sceneRepository;
        this.blockRepository = blockRepository;
        this.ruleRepository = ruleRepository;
        this.experienceFlowRepository = experienceFlowRepository;
        this.experienceVariableRepository = experienceVariableRepository;
        this.scanObjectSupport = scanObjectSupport;
        this.validator = validator;
    }

    @Transactional
    public ExperienceView execute(UUID projectId, int ifMatchVersion, ExperienceDocument document) {
        CatalogueProjectDto catalogueProject = projectAccessGuard.requireAccess(projectId);
        UUID orgId = UUID.fromString(catalogueProject.orgId());

        Project project = projectRepository.findById(projectId).orElseThrow();
        if (project.getDocVersion() != ifMatchVersion) {
            throw new StaleExperienceVersionException(projectId, ifMatchVersion, project.getDocVersion());
        }

        List<Scene> existingScenes = sceneRepository.findByProjectIdOrderByPositionAsc(projectId);
        List<Long> existingSceneIds = existingScenes.stream().map(Scene::getId).toList();
        List<Block> existingBlocks = blockRepository.findBySceneIdInOrderByPositionAsc(existingSceneIds);
        List<Rule> existingRules = ruleRepository.findBySceneIdInOrderByPositionAsc(existingSceneIds);
        ExperienceFlow existingFlow = experienceFlowRepository.findById(projectId).orElse(null);
        String existingOutput = existingFlow == null ? null : existingFlow.getOutput().name();

        ExperienceWriteContext context = new ExperienceWriteContext(
                existingScenes.stream().map(Scene::getSceneKey).collect(Collectors.toSet()),
                existingBlocks.stream().map(Block::getBlockKey).collect(Collectors.toSet()),
                existingRules.stream().map(Rule::getRuleKey).collect(Collectors.toSet()),
                project.getNextSceneSeq(),
                project.getNextBlockSeq(),
                project.getNextRuleSeq(),
                existingOutput);

        List<String> errors = new ArrayList<>(validator.validate(document, context).errors());
        errors.addAll(validateEnumValues(document));
        if (!errors.isEmpty()) {
            throw new ExperienceValidationException(errors);
        }

        Map<String, Scene> sceneByKey = existingScenes.stream()
                .collect(Collectors.toMap(Scene::getSceneKey, scene -> scene));
        Map<String, Block> blockByKey = existingBlocks.stream()
                .collect(Collectors.toMap(Block::getBlockKey, block -> block));
        Map<String, Rule> ruleByKey = existingRules.stream()
                .collect(Collectors.toMap(Rule::getRuleKey, rule -> rule));

        deleteRowsMissingFromDocument(document, existingScenes, existingBlocks, existingRules);

        List<Scene> savedScenes = new ArrayList<>();
        for (SceneDocument sceneDoc : document.scenes()) {
            Scene scene = upsertScene(sceneDoc, sceneByKey.get(sceneDoc.sceneKey()), project, orgId);
            sceneByKey.put(sceneDoc.sceneKey(), scene);
            savedScenes.add(scene);
        }

        List<Block> savedBlocks = new ArrayList<>();
        List<Rule> savedRules = new ArrayList<>();
        for (SceneDocument sceneDoc : document.scenes()) {
            Scene scene = sceneByKey.get(sceneDoc.sceneKey());
            for (BlockDocument blockDoc : sceneDoc.blocks()) {
                savedBlocks.add(upsertBlock(blockDoc, blockByKey.get(blockDoc.blockKey()), scene));
            }
            for (RuleDocument ruleDoc : sceneDoc.rules()) {
                savedRules.add(upsertRule(ruleDoc, ruleByKey.get(ruleDoc.ruleKey()), scene, orgId));
            }
        }

        ExperienceFlow savedFlow = saveFlow(projectId, existingFlow, document);
        List<ExperienceVariable> savedVariables = saveVariables(project, document);

        bumpProjectCounters(project, document);
        project.setDocVersion(project.getDocVersion() + 1);
        project = projectRepository.save(project);

        return buildView(project, savedFlow, savedVariables, savedScenes, savedBlocks, savedRules);
    }

    // A key already reported by validateKeys is skipped here - it's already going to fail the
    // whole write, so there's no need to also report it as an unparsable enum on top of that.
    private List<String> validateEnumValues(ExperienceDocument document) {
        List<String> errors = new ArrayList<>();
        for (SceneDocument scene : document.scenes()) {
            for (BlockDocument block : scene.blocks()) {
                if (!isValidEnum(BlockType.class, block.type())) {
                    errors.add("block '" + block.blockKey() + "' type '" + block.type() + "' is not a valid block type");
                }
            }
            for (RuleDocument rule : scene.rules()) {
                if (!isValidEnum(RuleEventType.class, rule.eventType())) {
                    errors.add("rule '" + rule.ruleKey() + "' eventType '" + rule.eventType() + "' is not a valid event type");
                }
                if (!isValidEnum(RuleAction.class, rule.action())) {
                    errors.add("rule '" + rule.ruleKey() + "' action '" + rule.action() + "' is not a valid action");
                }
            }
        }
        return errors;
    }

    private <E extends Enum<E>> boolean isValidEnum(Class<E> enumType, String value) {
        if (value == null) {
            return false;
        }
        try {
            Enum.valueOf(enumType, value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Children before parents: rules/blocks reference scenes via a real FK, so a scene being
    // removed must have its rules/blocks removed first. Rules and blocks have no FK between
    // them (trigger/target block keys are plain strings, validated at the document layer only),
    // so their own deletion order relative to each other doesn't matter.
    private void deleteRowsMissingFromDocument(
            ExperienceDocument document, List<Scene> existingScenes, List<Block> existingBlocks, List<Rule> existingRules
    ) {
        Set<String> documentSceneKeys = new HashSet<>();
        Set<String> documentBlockKeys = new HashSet<>();
        Set<String> documentRuleKeys = new HashSet<>();
        for (SceneDocument scene : document.scenes()) {
            documentSceneKeys.add(scene.sceneKey());
            for (BlockDocument block : scene.blocks()) {
                documentBlockKeys.add(block.blockKey());
            }
            for (RuleDocument rule : scene.rules()) {
                documentRuleKeys.add(rule.ruleKey());
            }
        }

        List<Rule> rulesToDelete = existingRules.stream()
                .filter(rule -> !documentRuleKeys.contains(rule.getRuleKey()))
                .toList();
        List<Block> blocksToDelete = existingBlocks.stream()
                .filter(block -> !documentBlockKeys.contains(block.getBlockKey()))
                .toList();
        List<Scene> scenesToDelete = existingScenes.stream()
                .filter(scene -> !documentSceneKeys.contains(scene.getSceneKey()))
                .toList();

        ruleRepository.deleteAll(rulesToDelete);
        blockRepository.deleteAll(blocksToDelete);
        sceneRepository.deleteAll(scenesToDelete);
    }

    private Scene upsertScene(SceneDocument sceneDoc, Scene existing, Project project, UUID orgId) {
        Scene scene = existing;
        if (scene == null) {
            scene = new Scene();
            scene.setProject(project);
            scene.setSceneKey(sceneDoc.sceneKey());
        }
        scene.setName(sceneDoc.name());
        scene.setPosition(sceneDoc.position());
        scene.setStart(sceneDoc.isStart());
        ScanObjectType initCardType = scanObjectSupport.resolveType(orgId, sceneDoc.initCardTypeId());
        scene.setInitCardType(initCardType);
        return sceneRepository.save(scene);
    }

    private Block upsertBlock(BlockDocument blockDoc, Block existing, Scene scene) {
        Block block = existing;
        if (block == null) {
            block = new Block();
            block.setBlockKey(blockDoc.blockKey());
        }
        block.setScene(scene);
        block.setType(BlockType.valueOf(blockDoc.type()));
        block.setPosition(blockDoc.position());
        block.setContent(blockDoc.content());
        return blockRepository.save(block);
    }

    private Rule upsertRule(RuleDocument ruleDoc, Rule existing, Scene scene, UUID orgId) {
        Rule rule = existing;
        if (rule == null) {
            rule = new Rule();
            rule.setRuleKey(ruleDoc.ruleKey());
        }
        rule.setScene(scene);
        rule.setEventType(RuleEventType.valueOf(ruleDoc.eventType()));
        rule.setAction(RuleAction.valueOf(ruleDoc.action()));
        rule.setPosition(ruleDoc.position());
        ScanObjectType scanObjectType = scanObjectSupport.resolveType(orgId, ruleDoc.scanObjectTypeId());
        rule.setScanObjectType(scanObjectType);
        rule.setTriggerBlockKey(ruleDoc.triggerBlockKey());
        rule.setTargetSceneKey(ruleDoc.targetSceneKey());
        rule.setTargetBlockKey(ruleDoc.targetBlockKey());
        return ruleRepository.save(rule);
    }

    // output is only ever set on the first save; the validator already refused any change to it.
    private ExperienceFlow saveFlow(UUID projectId, ExperienceFlow existingFlow, ExperienceDocument document) {
        ExperienceFlow flow = existingFlow;
        if (flow == null) {
            flow = new ExperienceFlow();
            flow.setProjectId(projectId);
            flow.setOutput(ExperienceOutput.valueOf(document.output()));
        }
        flow.setSchemaVersion(document.flow().schemaVersion());
        flow.setFlow(document.flow().content());
        return experienceFlowRepository.save(flow);
    }

    // Matched by key, like scenes/blocks/rules: an existing key is updated in place, never
    // deleted and re-inserted, so the (project_id, variable_key) unique constraint can't trip
    // over Hibernate flushing inserts before deletes.
    private List<ExperienceVariable> saveVariables(Project project, ExperienceDocument document) {
        List<ExperienceVariable> existing = experienceVariableRepository.findByProjectIdOrderByVariableKeyAsc(project.getId());
        Map<String, ExperienceVariable> existingByKey = existing.stream()
                .collect(Collectors.toMap(ExperienceVariable::getVariableKey, variable -> variable));
        Set<String> documentKeys = document.variables().stream()
                .map(VariableDocument::key)
                .collect(Collectors.toSet());

        List<ExperienceVariable> toDelete = existing.stream()
                .filter(variable -> !documentKeys.contains(variable.getVariableKey()))
                .toList();
        experienceVariableRepository.deleteAll(toDelete);

        List<ExperienceVariable> saved = new ArrayList<>();
        for (VariableDocument variableDoc : document.variables()) {
            ExperienceVariable variable = existingByKey.get(variableDoc.key());
            if (variable == null) {
                variable = new ExperienceVariable();
                variable.setProject(project);
                variable.setVariableKey(variableDoc.key());
            }
            variable.setKind(VariableKind.valueOf(variableDoc.kind()));
            variable.setInitialValue(variableDoc.initial());
            saved.add(experienceVariableRepository.save(variable));
        }
        saved.sort(Comparator.comparing(ExperienceVariable::getVariableKey));
        return saved;
    }

    // Bumped past the highest key actually used by this write, per project.Java's own contract -
    // never bumped backwards, since an update-only write (no new keys) must leave it untouched.
    private void bumpProjectCounters(Project project, ExperienceDocument document) {
        int maxSceneKey = 0;
        int maxBlockKey = 0;
        int maxRuleKey = 0;
        for (SceneDocument scene : document.scenes()) {
            maxSceneKey = Math.max(maxSceneKey, Integer.parseInt(scene.sceneKey()));
            for (BlockDocument block : scene.blocks()) {
                maxBlockKey = Math.max(maxBlockKey, Integer.parseInt(block.blockKey()));
            }
            for (RuleDocument rule : scene.rules()) {
                maxRuleKey = Math.max(maxRuleKey, Integer.parseInt(rule.ruleKey()));
            }
        }
        project.setNextSceneSeq(Math.max(project.getNextSceneSeq(), maxSceneKey + 1));
        project.setNextBlockSeq(Math.max(project.getNextBlockSeq(), maxBlockKey + 1));
        project.setNextRuleSeq(Math.max(project.getNextRuleSeq(), maxRuleKey + 1));
    }

    private ExperienceView buildView(
            Project project, ExperienceFlow flow, List<ExperienceVariable> variables,
            List<Scene> scenes, List<Block> blocks, List<Rule> rules
    ) {
        Map<Long, List<Block>> blocksByScene = blocks.stream()
                .collect(Collectors.groupingBy(block -> block.getScene().getId()));
        Map<Long, List<Rule>> rulesByScene = rules.stream()
                .collect(Collectors.groupingBy(rule -> rule.getScene().getId()));

        List<ExperienceView.SceneView> sceneViews = scenes.stream()
                .sorted(Comparator.comparing(Scene::getPosition))
                .map(scene -> new ExperienceView.SceneView(
                        scene,
                        sortByPosition(blocksByScene.getOrDefault(scene.getId(), List.of()), Block::getPosition),
                        sortByPosition(rulesByScene.getOrDefault(scene.getId(), List.of()), Rule::getPosition)))
                .toList();

        return new ExperienceView(
                project.getId(), project.getDocVersion(),
                project.getNextSceneSeq(), project.getNextBlockSeq(), project.getNextRuleSeq(),
                flow, variables, sceneViews);
    }

    private <T> List<T> sortByPosition(List<T> items, java.util.function.Function<T, Integer> position) {
        return items.stream().sorted(Comparator.comparing(position)).toList();
    }
}
