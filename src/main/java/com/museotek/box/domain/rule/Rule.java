package com.museotek.box.domain.rule;

import com.museotek.box.domain.scanobject.ScanObjectType;
import com.museotek.box.domain.scene.Scene;
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

@Entity
@Table(name = "rules")
@Getter
@Setter
@NoArgsConstructor
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false)
    private String ruleKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RuleAction action;

    @Column(nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_object_type_id")
    private ScanObjectType scanObjectType;

    // Validated against the document by application code, not DB foreign keys — see the proposal doc.
    @Column
    private String triggerBlockKey;

    @Column
    private String targetSceneKey;

    @Column
    private String targetBlockKey;
}
