package com.museotek.box.application.experience;

import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleEffect;
import com.museotek.box.domain.rule.RuleTrigger;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What a saved experience still misses before a Box can run it: the choices a curator may leave
 * open while editing (a save accepts them) but which must be made before it is assigned to a Box.
 * The frontend shows its own to-fix banner; this is the backend's last check, so a Box never gets
 * an experience it can't play.
 * <ul>
 *   <li>never saved;</li>
 *   <li>the start scene has no start card (its init card type);</li>
 *   <li>a media block with no file chosen;</li>
 *   <li>a SCAN rule with no card chosen;</li>
 *   <li>a VIDEO_ENDED rule in a scene that plays no video;</li>
 *   <li>a BOX_AUDIO effect with no sound chosen.</li>
 * </ul>
 */
@Service
public class ExperienceGaps {

    private final SceneRepository sceneRepository;
    private final BlockRepository blockRepository;
    private final RuleRepository ruleRepository;

    public ExperienceGaps(SceneRepository sceneRepository, BlockRepository blockRepository, RuleRepository ruleRepository) {
        this.sceneRepository = sceneRepository;
        this.blockRepository = blockRepository;
        this.ruleRepository = ruleRepository;
    }

    public List<String> find(UUID projectId) {
        List<Scene> scenes = sceneRepository.findByProjectIdOrderByPositionAsc(projectId);
        if (scenes.isEmpty()) {
            return List.of("the experience has not been saved yet");
        }

        List<String> gaps = new ArrayList<>();
        for (Scene scene : scenes) {
            if (scene.isStart() && scene.getInitCardType() == null) {
                gaps.add(label(scene) + " is the start scene but has no start card");
            }
        }

        List<Long> sceneIds = scenes.stream().map(Scene::getId).toList();
        for (String blockKey : blockRepository.findMediaBlockKeysWithoutMedia(sceneIds)) {
            gaps.add("block '" + blockKey + "' has no file chosen");
        }

        Map<Long, Scene> sceneById = scenes.stream().collect(Collectors.toMap(Scene::getId, Function.identity()));
        Set<Long> scenesWithVideo = new HashSet<>(blockRepository.findSceneIdsWithVideo(sceneIds));
        for (Rule rule : ruleRepository.findBySceneIdInOrderByPositionAsc(sceneIds)) {
            Long sceneId = rule.getScene().getId();
            String ruleLabel = "rule '" + rule.getRuleKey() + "' in " + label(sceneById.get(sceneId));
            if (rule.getTriggerType() == RuleTrigger.SCAN && rule.getScanObjectType() == null) {
                gaps.add(ruleLabel + " waits for a card, but no card is chosen");
            }
            if (rule.getTriggerType() == RuleTrigger.VIDEO_ENDED && !scenesWithVideo.contains(sceneId)) {
                gaps.add(ruleLabel + " waits for a video to end, but the scene plays no video");
            }
            for (RuleEffect effect : rule.getEffects()) {
                if ("BOX_AUDIO".equals(effect.type()) && isBlank(effect.url())) {
                    gaps.add(ruleLabel + " plays a sound on the Box, but no sound is chosen");
                }
            }
        }
        return gaps;
    }

    private static String label(Scene scene) {
        return "scene '" + scene.getSceneKey() + "' (" + scene.getName() + ")";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
