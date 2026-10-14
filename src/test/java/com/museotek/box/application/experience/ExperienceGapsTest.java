package com.museotek.box.application.experience;

import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleDestination;
import com.museotek.box.domain.rule.RuleEffect;
import com.museotek.box.domain.rule.RuleTrigger;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
import com.museotek.box.infrastructure.repository.BlockRepository;
import com.museotek.box.infrastructure.repository.RuleRepository;
import com.museotek.box.infrastructure.repository.SceneRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExperienceGapsTest {

    private final SceneRepository sceneRepository = mock(SceneRepository.class);
    private final BlockRepository blockRepository = mock(BlockRepository.class);
    private final RuleRepository ruleRepository = mock(RuleRepository.class);
    private final ExperienceGaps gaps = new ExperienceGaps(sceneRepository, blockRepository, ruleRepository);

    private final UUID projectId = UUID.randomUUID();

    @Test
    void neverSaved_isAGap() {
        when(sceneRepository.findByProjectIdOrderByPositionAsc(projectId)).thenReturn(List.of());

        assertThat(gaps.find(projectId)).containsExactly("the experience has not been saved yet");
    }

    @Test
    void complete_hasNoGaps() {
        Scene start = scene(1L, "1", "Start", true, card(7L));
        Scene video = scene(2L, "2", "Film", false, null);
        stubScenes(start, video);
        when(blockRepository.findSceneIdsWithVideo(anyCollection())).thenReturn(List.of(2L));
        when(ruleRepository.findBySceneIdInOrderByPositionAsc(anyCollection())).thenReturn(List.of(
                rule(start, "1", RuleTrigger.SCAN, card(8L), List.of(boxAudio("a6f0c1d2-0000-0000-0000-000000000001"))),
                rule(video, "2", RuleTrigger.VIDEO_ENDED, null, List.of())));

        assertThat(gaps.find(projectId)).isEmpty();
    }

    @Test
    void startSceneWithoutStartCard_isAGap() {
        stubScenes(scene(1L, "1", "Start", true, null));

        assertThat(gaps.find(projectId)).containsExactly("scene '1' (Start) is the start scene but has no start card");
    }

    @Test
    void mediaBlockWithoutFile_isAGap() {
        stubScenes(scene(1L, "1", "Start", true, card(7L)));
        when(blockRepository.findMediaBlockKeysWithoutMedia(anyCollection())).thenReturn(List.of("4"));

        assertThat(gaps.find(projectId)).containsExactly("block '4' has no file chosen");
    }

    @Test
    void ruleGaps_areReported() {
        Scene start = scene(1L, "1", "Start", true, card(7L));
        stubScenes(start);
        when(ruleRepository.findBySceneIdInOrderByPositionAsc(anyCollection())).thenReturn(List.of(
                rule(start, "1", RuleTrigger.SCAN, null, List.of()),
                rule(start, "2", RuleTrigger.VIDEO_ENDED, null, List.of()),
                rule(start, "3", RuleTrigger.SCENE_ENTERED, null, List.of(boxAudio(null)))));

        assertThat(gaps.find(projectId)).containsExactly(
                "rule '1' in scene '1' (Start) waits for a card, but no card is chosen",
                "rule '2' in scene '1' (Start) waits for a video to end, but the scene plays no video",
                "rule '3' in scene '1' (Start) plays a sound on the Box, but no sound is chosen");
    }

    private void stubScenes(Scene... scenes) {
        when(sceneRepository.findByProjectIdOrderByPositionAsc(projectId)).thenReturn(List.of(scenes));
    }

    private static Scene scene(Long id, String key, String name, boolean start, ScanObjectType initCard) {
        Scene scene = new Scene();
        scene.setId(id);
        scene.setSceneKey(key);
        scene.setName(name);
        scene.setStart(start);
        scene.setInitCardType(initCard);
        return scene;
    }

    private static ScanObjectType card(Long id) {
        ScanObjectType card = new ScanObjectType();
        card.setId(id);
        return card;
    }

    private static Rule rule(Scene scene, String key, RuleTrigger trigger, ScanObjectType card, List<RuleEffect> effects) {
        Rule rule = new Rule();
        rule.setScene(scene);
        rule.setRuleKey(key);
        rule.setTriggerType(trigger);
        rule.setScanObjectType(card);
        rule.setEffects(effects);
        rule.setDestinationType(RuleDestination.STAY);
        return rule;
    }

    private static RuleEffect boxAudio(String url) {
        return new RuleEffect("BOX_AUDIO", null, null, null, null, null, null, null, null, url);
    }
}
