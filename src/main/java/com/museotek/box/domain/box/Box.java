package com.museotek.box.domain.box;

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
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "boxes")
@Getter
@Setter
@NoArgsConstructor
public class Box {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "serial_number", nullable = false, unique = true, length = 255)
    private String serialNumber;

    // SHA-256 (hex) of the Box's login key; null until a manager makes one. The key itself is shown once.
    @Column(name = "key_hash", unique = true, length = 64)
    private String keyHash;

    @Column(name = "key_created_at")
    private Instant keyCreatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 255)
    private BoxStatus status = BoxStatus.OFFLINE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_project_id")
    private Project currentProject;

    // Scene of the running experience, as the player project last reported it.
    @Column(name = "current_scene_key", length = 255)
    private String currentSceneKey;

    // When we last heard from the player project about this Box, even with a report that came too late to apply.
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    // When the applied status report happened (its "at"), so a late report never overwrites a newer one.
    @Column(name = "status_at")
    private Instant statusAt;

    @ManyToMany
    @JoinTable(
            name = "box_projects",
            joinColumns = @JoinColumn(name = "box_id"),
            inverseJoinColumns = @JoinColumn(name = "project_id")
    )
    private Set<Project> assignedProjects = new HashSet<>();
}
