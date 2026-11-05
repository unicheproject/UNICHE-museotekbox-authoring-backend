package com.museotek.box.application.publish;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.museotek.box.application.experience.ExperienceView;
import com.museotek.box.domain.block.Block;
import com.museotek.box.domain.block.BlockType;
import com.museotek.box.domain.experience.ExperienceFlow;
import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.experience.ExperienceVariable;
import com.museotek.box.domain.experience.VariableKind;
import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleCondition;
import com.museotek.box.domain.rule.RuleDestination;
import com.museotek.box.domain.rule.RuleEffect;
import com.museotek.box.domain.rule.RuleTrigger;
import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublishedContentMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PublishedContentMapper mapper = new PublishedContentMapper(objectMapper);

    private final UUID video = UUID.randomUUID();
    private final UUID sound = UUID.randomUUID();

    @Test
    void keepsWhatABoxPlays_scenesBlocksRulesAndVariables() {
        PublishedContent content = mapper.toContent(quiz());

        assertThat(content.contentSchemaVersion()).isEqualTo(PublishedContent.SCHEMA_VERSION);
        assertThat(content.output()).isEqualTo("DISPLAY");
        assertThat(content.variables()).containsExactly(new PublishedContent.Variable("correct", "NUMBER", "0"));

        PublishedContent.Scene start = content.scenes().get(0);
        assertThat(start.sceneKey()).isEqualTo("1");
        assertThat(start.isStart()).isTrue();
        assertThat(start.initCardTypeId()).isEqualTo(7L);
        assertThat(start.blocks()).extracting(PublishedContent.Block::type).containsExactly("VIDEO");

        PublishedContent.Rule rule = start.rules().get(0);
        assertThat(rule.trigger()).isEqualTo(new PublishedContent.Trigger("SCAN", 8L, null));
        assertThat(rule.conditions()).containsExactly(new RuleCondition("VAR_CMP", "correct", "GTE", "2"));
        assertThat(rule.destination()).isEqualTo(new PublishedContent.Destination("GO_TO", "2", null));
    }

    @Test
    void json_embedsBlockContentAsJson_andLeavesTheWizardFlowOut() throws Exception {
        JsonNode json = objectMapper.readTree(mapper.toJson(mapper.toContent(quiz())));

        assertThat(json.get("contentSchemaVersion").asInt()).isEqualTo(1);
        assertThat(json.at("/scenes/0/blocks/0/content/mediaId").asText()).isEqualTo(video.toString());
        assertThat(json.has("flow")).isFalse();
        assertThat(json.has("nextSceneSeq")).isFalse();
    }

    @Test
    void mediaIds_listBlockFilesAndEffectSounds_onlyRealIds() {
        PublishedContent content = mapper.toContent(quiz());

        assertThat(mapper.mediaIds(content)).containsExactlyInAnyOrder(video.toString(), sound.toString());
    }

    @Test
    void cardTypeIds_listStartCardsAndScanRuleCards() {
        PublishedContent content = mapper.toContent(quiz());

        assertThat(mapper.cardTypeIds(content)).containsExactly(7L, 8L);
    }

    // Start scene: a video, started by card 7; a SCAN rule for card 8 that plays a sound. Second scene: text only.
    private ExperienceView quiz() {
        ExperienceFlow flow = new ExperienceFlow();
        flow.setOutput(ExperienceOutput.DISPLAY);
        ExperienceVariable correct = new ExperienceVariable();
        correct.setVariableKey("correct");
        correct.setKind(VariableKind.NUMBER);
        correct.setInitialValue("0");

        Scene start = scene("1", true, card(7L));
        Block videoBlock = block("b1", BlockType.VIDEO, "{\"mediaId\":\"" + video + "\"}");
        Rule scan = new Rule();
        scan.setRuleKey("r1");
        scan.setPosition(0);
        scan.setTriggerType(RuleTrigger.SCAN);
        scan.setScanObjectType(card(8L));
        scan.setConditions(List.of(new RuleCondition("VAR_CMP", "correct", "GTE", "2")));
        scan.setEffects(List.of(
                new RuleEffect("BOX_AUDIO", null, null, null, null, null, null, null, null, sound.toString()),
                new RuleEffect("REPLY", null, null, null, "green", "Well done", null, "", null, null)));
        scan.setDestinationType(RuleDestination.GO_TO);
        scan.setTargetSceneKey("2");

        Scene second = scene("2", false, null);
        Block text = block("b2", BlockType.TEXT, "{\"text\":\"Hello\"}");

        return new ExperienceView(UUID.randomUUID(), 3, 3, 3, 2, flow, List.of(correct), List.of(
                new ExperienceView.SceneView(start, List.of(videoBlock), List.of(scan)),
                new ExperienceView.SceneView(second, List.of(text), List.of())));
    }

    private static Scene scene(String key, boolean start, ScanObjectType startCard) {
        Scene scene = new Scene();
        scene.setSceneKey(key);
        scene.setName("Scene " + key);
        scene.setPosition(Integer.parseInt(key));
        scene.setStart(start);
        scene.setInitCardType(startCard);
        return scene;
    }

    private static Block block(String key, BlockType type, String content) {
        Block block = new Block();
        block.setBlockKey(key);
        block.setType(type);
        block.setPosition(0);
        block.setContent(content);
        return block;
    }

    private static ScanObjectType card(Long id) {
        ScanObjectType card = new ScanObjectType();
        card.setId(id);
        return card;
    }
}
