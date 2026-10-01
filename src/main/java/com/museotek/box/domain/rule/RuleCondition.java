package com.museotek.box.domain.rule;

/**
 * The optional "if" of a rule, tested against the experience's variables. Two types:
 * <ul>
 *   <li>{@code VAR_CMP}: a {@code NUMBER} variable compared with {@code value} (an integer) using
 *       {@code op}: {@code GTE}, {@code LTE}, {@code GT}, {@code LT}, {@code EQ} or {@code NEQ}.
 *       Example: {@code correct GTE 2}.</li>
 *   <li>{@code FLAG_IS}: a {@code FLAG} variable equals {@code value} ({@code true}/{@code false}).</li>
 * </ul>
 * Plain strings on purpose, like the rest of the experience document: checked by
 * {@code ExperienceDocumentValidator}, stored as JSON on the rule.
 */
public record RuleCondition(String type, String variable, String op, String value) {
}
