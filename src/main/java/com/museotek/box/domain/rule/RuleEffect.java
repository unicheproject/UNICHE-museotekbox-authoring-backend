package com.museotek.box.domain.rule;

/**
 * One thing a rule does, in order with the rule's other effects. Each type uses only some fields:
 * <ul>
 *   <li>{@code REPLY}: the coloured reply panel and/or its sound: {@code color} (required),
 *       {@code text}, {@code media}, {@code audio}, {@code boxVideo}.</li>
 *   <li>{@code BOX_SCREEN}: text on the Box's own display: {@code text} (required).</li>
 *   <li>{@code BOX_AUDIO}: a sound from the Box: {@code url}.</li>
 *   <li>{@code SET_NUMBER}: sets a {@code NUMBER} variable to {@code value} (an integer).</li>
 *   <li>{@code ADD_NUMBER}: adds {@code amount} to a {@code NUMBER} variable.</li>
 *   <li>{@code SET_FLAG}: sets a {@code FLAG} variable to {@code value} ({@code true}/{@code false}).</li>
 * </ul>
 * Plain strings on purpose, like the rest of the experience document: checked by
 * {@code ExperienceDocumentValidator}, stored as JSON on the rule.
 */
public record RuleEffect(
        String type,
        String variable,
        String value,
        Integer amount,
        String color,
        String text,
        String media,
        String audio,
        String boxVideo,
        String url
) {
}
