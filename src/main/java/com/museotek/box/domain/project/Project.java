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
 *
 * <p><b>Project = experience.</b> "Project" is the platform's word, "experience" is what a
 * curator builds inside it. There is no separate {@code Experience} entity: the experience is
 * the {@code Scene}/{@code Block}/{@code Rule} rows that point to this project.
 *
 * <p>Catalogue owns the project as a platform item (name, status, members). This row keeps only
 * what MuseotekBox needs:
 * <ul>
 *   <li>something for our own tables to reference ({@code scenes}, {@code box_projects},
 *       {@code boxes.current_project_id});</li>
 *   <li>the experience's bookkeeping: {@link #docVersion} for the save conflict check, and the
 *       key counters for new scenes/blocks/rules.</li>
 * </ul>
 * {@link #name} and {@link #orgId} are copies, refreshed by {@code ProjectAccessGuard} whenever
 * the project is opened. API responses never read the name from here. They read it live from
 * Catalogue. See README, "Project vs Experience".
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

    // Bumped on every experience-document write; the client echoes it back as If-Match.
    @Column(nullable = false)
    private int docVersion;

    // Next unused scene_key/block_key/rule_key value for this project; a client-invented key
    // for a new row must be >= this. Bumped past the highest key used by every write.
    @Column(nullable = false)
    private int nextSceneSeq = 1;

    @Column(nullable = false)
    private int nextBlockSeq = 1;

    @Column(nullable = false)
    private int nextRuleSeq = 1;
}
