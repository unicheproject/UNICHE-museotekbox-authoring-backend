package com.museotek.box.web.experience;

import com.museotek.box.domain.rule.Rule;
import com.museotek.box.domain.scanobject.ScanObjectType;

public record RuleResponse(
        String ruleKey,
        String eventType,
        String action,
        Integer position,
        Long scanObjectTypeId,
        String triggerBlockKey,
        String targetSceneKey,
        String targetBlockKey
) {

    public static RuleResponse from(Rule rule) {
        ScanObjectType scanObjectType = rule.getScanObjectType();
        Long scanObjectTypeId;
        if (scanObjectType != null) {
            scanObjectTypeId = scanObjectType.getId();
        } else {
            scanObjectTypeId = null;
        }
        return new RuleResponse(
                rule.getRuleKey(),
                rule.getEventType().name(),
                rule.getAction().name(),
                rule.getPosition(),
                scanObjectTypeId,
                rule.getTriggerBlockKey(),
                rule.getTargetSceneKey(),
                rule.getTargetBlockKey());
    }
}
