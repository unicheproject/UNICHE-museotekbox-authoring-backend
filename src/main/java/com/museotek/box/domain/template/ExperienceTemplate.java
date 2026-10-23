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
    @Column(name = "id", nullable = false)
    private UUID id;

    // Empty for a global template.
    @Column(name = "org_id")
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 31)
    private ExperienceTemplateType type;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    // The frontend form's starting values. Its shape belongs to the frontend; only checked to be a JSON object.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "defaults", nullable = false)
    private String defaults;

    // The creator's JWT subject, like users.subject. Empty for global templates.
    @Column(name = "created_by", length = 255)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public boolean isGlobal() {
        return orgId == null;
    }
}
