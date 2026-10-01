package com.museotek.box.web.experience;

import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.rule.RuleCondition;
import com.museotek.box.domain.rule.RuleEffect;
import com.museotek.box.domain.scanobject.ScanObjectType;

import java.util.List;

// Same "when ... if ... do ... then" shape as the write request, so a client can send back what it read.
public record RuleResponse(
        String ruleKey,
        Integer position,
        TriggerResponse trigger,
        RuleCondition condition,
        List<RuleEffect> effects,
        DestinationResponse destination
) {

    public record TriggerResponse(String type, Long scanObjectTypeId, Integer seconds) {
    }

    public record DestinationResponse(String type, String targetSceneKey) {
    }

    public static RuleResponse from(Rule rule) {
        ScanObjectType scanObjectType = rule.getScanObjectType();
        Long scanObjectTypeId;
        if (scanObjectType != null) {
            scanObjectTypeId = scanObjectType.getId();
        } else {
            scanObjectTypeId = null;
        }
        TriggerResponse trigger = new TriggerResponse(rule.getTriggerType().name(), scanObjectTypeId, rule.getTriggerSeconds());
        DestinationResponse destination = new DestinationResponse(rule.getDestinationType().name(), rule.getTargetSceneKey());
        return new RuleResponse(rule.getRuleKey(), rule.getPosition(), trigger, rule.getCondition(), rule.getEffects(), destination);
    }
}
