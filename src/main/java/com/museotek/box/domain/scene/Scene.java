package com.museotek.box.domain.scene;

import com.museotek.box.domain.project.Project;
import com.museotek.box.domain.scanobject.ScanObjectType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "scenes")
@Getter
@Setter
@NoArgsConstructor
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String sceneKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "is_start", nullable = false)
    private boolean start;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "init_card_type_id")
    private ScanObjectType initCardType;
}
