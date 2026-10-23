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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rules")
@Getter
@Setter
@NoArgsConstructor
public class Rule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(name = "rule_key", nullable = false, length = 64)
    private String ruleKey;

    @Column(name = "position", nullable = false)
    private Integer position;

    // When: the trigger, plus what some triggers need (the card for SCAN, the seconds for TIMER_ELAPSED).
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 31)
    private RuleTrigger triggerType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_object_type_id")
    private ScanObjectType scanObjectType;

    @Column(name = "trigger_seconds")
    private Integer triggerSeconds;

    // If: null means always.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "condition")
    private RuleCondition condition;

    // Do: in order, possibly none.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "effects", nullable = false)
    private List<RuleEffect> effects = new ArrayList<>();

    // Then. targetSceneKey is set only for GO_TO; validated against the document by application
    // code, not a DB foreign key (see the proposal doc).
    @Enumerated(EnumType.STRING)
    @Column(name = "destination_type", nullable = false, length = 15)
    private RuleDestination destinationType;

    @Column(name = "target_scene_key", length = 64)
    private String targetSceneKey;
}
