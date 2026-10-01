package com.museotek.box.domain.rule;

/** Where the visitor goes after a rule ran. */
public enum RuleDestination {
    /** Another scene, named by the rule's target scene key. */
    GO_TO,
    /** Stay in this scene. */
    STAY,
    /** End the visit: the Box goes back to idle. */
    END
}
