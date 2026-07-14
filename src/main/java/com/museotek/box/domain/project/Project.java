package com.museotek.box.domain.project;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Local companion row for a Catalogue project, keyed by the Catalogue's own project UUID
 * (no separate id-mapping table). Populated via "create-up" and "lazy-JIT" on access;
 * soft-deleted when Catalogue no longer recognises the id.
 */
@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
public class Project {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orgId;

    @Column(nullable = false)
    private String name;

    @Column
    private Instant deletedAt;
}
