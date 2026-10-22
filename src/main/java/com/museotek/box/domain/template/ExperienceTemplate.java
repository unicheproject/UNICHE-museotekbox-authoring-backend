package com.museotek.box.domain.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A ready-made starting point for the wizard: one {@link ExperienceTemplateType} plus the
 * settings its form opens with. A global template ({@link #orgId} empty) is for every
 * organisation and only changes through Liquibase; an organisation's own templates are managed
 * by its managers. Nothing points at a template: an experience keeps its own settings in its
 * flow, so changing or deleting a template never changes an experience.
 */
@Entity
@Table(name = "experience_templates")
@Getter
@Setter
@NoArgsConstructor
public class ExperienceTemplate {

    @Id
    private UUID id;

    // Empty for a global template.
    @Column
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceTemplateType type;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    // The frontend form's starting values. Its shape belongs to the frontend; only checked to be a JSON object.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String defaults;

    // The creator's JWT subject, like users.subject. Empty for global templates.
    @Column
    private String createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public boolean isGlobal() {
        return orgId == null;
    }
}
