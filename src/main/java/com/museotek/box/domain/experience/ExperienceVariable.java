package com.museotek.box.domain.experience;

import com.museotek.box.domain.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A number or flag the experience keeps while a visitor plays it, e.g. the Quiz's
 * {@code correct}/{@code wrong}/{@code hints} counters. Rule effects change it, rule conditions
 * test it. {@link #initialValue} is what it holds when the visit starts: an integer for
 * {@code NUMBER}, {@code true}/{@code false} for {@code FLAG}.
 */
@Entity
@Table(name = "experience_variables")
@Getter
@Setter
@NoArgsConstructor
public class ExperienceVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String variableKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VariableKind kind;

    @Column(nullable = false)
    private String initialValue;
}
