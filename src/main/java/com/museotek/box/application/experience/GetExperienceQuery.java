package com.museotek.box.application.experience;

import com.museotek.box.application.projectaccess.ProjectAccessGuard;
import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.ProjectRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class GetExperienceQuery {

    private final ProjectAccessGuard projectAccessGuard;
    private final ProjectRepository projectRepository;
    private final SceneRepository sceneRepository;
    private final BlockRepository blockRepository;
    private final RuleRepository ruleRepository;

    public GetExperienceQuery(
            ProjectAccessGuard projectAccessGuard,
            ProjectRepository projectRepository,
            SceneRepository sceneRepository,
            BlockRepository blockRepository,
            RuleRepository ruleRepository
    ) {
        this.projectAccessGuard = projectAccessGuard;
        this.projectRepository = projectRepository;
        this.sceneRepository = sceneRepository;
        this.blockRepository = blockRepository;
        this.ruleRepository = ruleRepository;
    }

    public ExperienceView execute(UUID projectId) {
        projectAccessGuard.requireAccess(projectId);

        Project project = projectRepository.findById(projectId).orElseThrow();
        int version = project.getDocVersion();
        int nextSceneSeq = project.getNextSceneSeq();
        int nextBlockSeq = project.getNextBlockSeq();
        int nextRuleSeq = project.getNextRuleSeq();

        List<Scene> scenes = sceneRepository.findByProjectIdOrderByPositionAsc(projectId);
        if (scenes.isEmpty()) {
            return new ExperienceView(projectId, version, nextSceneSeq, nextBlockSeq, nextRuleSeq, List.of());
        }

        List<Long> sceneIds = scenes.stream().map(Scene::getId).toList();
        Map<Long, List<Block>> blocksByScene = blockRepository.findBySceneIdInOrderByPositionAsc(sceneIds).stream()
                .collect(Collectors.groupingBy(block -> block.getScene().getId()));
        Map<Long, List<Rule>> rulesByScene = ruleRepository.findBySceneIdInOrderByPositionAsc(sceneIds).stream()
                .collect(Collectors.groupingBy(rule -> rule.getScene().getId()));

        List<ExperienceView.SceneView> sceneViews = scenes.stream()
                .map(scene -> new ExperienceView.SceneView(
                        scene,
                        blocksByScene.getOrDefault(scene.getId(), List.of()),
                        rulesByScene.getOrDefault(scene.getId(), List.of())))
                .toList();
        return new ExperienceView(projectId, version, nextSceneSeq, nextBlockSeq, nextRuleSeq, sceneViews);
    }
}
