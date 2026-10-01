package com.museotek.box.domain.experience;

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

import java.util.UUID;

/**
 * The curator's source for an experience: what they built in the wizard (start, activities
 * from templates, hand-overs, end, replies), before the frontend turns it into scenes, blocks
 * and rules. One per project, same id.
 *
 * <p>Stored as-is and never interpreted here: the conversion only goes one way, so the wizard
 * needs this to reopen an experience. The backend checks only {@link #schemaVersion}. Always
 * saved together with the graph, under the project's {@code docVersion}.
 *
 * <p>{@link #output} is not part of the flow: it is fixed when the experience is created and
 * checked by the backend (see {@code ExperienceDocumentValidator}).
 */
@Entity
@Table(name = "experience_flows")
@Getter
@Setter
@NoArgsConstructor
public class ExperienceFlow {

    @Id
    private UUID projectId;

    @Column(nullable = false)
    private Integer schemaVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String flow;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExperienceOutput output;
}
