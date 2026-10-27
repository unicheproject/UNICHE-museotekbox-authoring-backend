package com.museotek.box.domain.rule;

/** Where the visitor goes after a rule ran. */
public enum RuleDestination {
    /** Another scene, named by the rule's target scene key. */
    GO_TO,
    /** One of the rule's target scene keys, picked at random by the Box each time. */
    GO_TO_RANDOM,
    /** Stay in this scene. */
    STAY,
    /** End the visit: the Box goes back to idle. */
    END
}
