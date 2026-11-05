package com.museotek.box.domain.publish;

import com.museotek.box.domain.experience.ExperienceOutput;
import com.museotek.box.domain.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A frozen copy of an experience, made when it is published. Boxes play the latest copy of each
 * experience, never the draft, so curators can keep editing. Never changed after it is made:
 * publishing again adds the next version.
 */
@Entity
@Table(name = "published_experiences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PublishedExperience {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // 1 for the first publish of the experience, then +1 each time.
    @Column(name = "version", nullable = false)
    private int version;

    // The draft's doc_version the copy was made from: a newer draft means unpublished changes.
    @Column(name = "doc_version", nullable = false)
    private int docVersion;

    // Catalogue's project name at publish time, so a Box never needs Catalogue.
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "output", nullable = false, length = 15)
    private ExperienceOutput output;

    @Column(name = "start_card_type_id", nullable = false)
    private Long startCardTypeId;

    // What the Box downloads: PublishedContent as JSON.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content", nullable = false)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media_ids", nullable = false)
    private List<String> mediaIds;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "card_type_ids", nullable = false)
    private List<Long> cardTypeIds;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    // The publisher's JWT subject, like media.uploaded_by. Empty once the user is erased (GDPR).
    @Column(name = "published_by", length = 255)
    private String publishedBy;

    public PublishedExperience(Project project, int version, String name, ExperienceOutput output, Long startCardTypeId,
                               String content, List<String> mediaIds, List<Long> cardTypeIds,
                               Instant publishedAt, String publishedBy) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.version = version;
        this.docVersion = project.getDocVersion();
        this.name = name;
        this.output = output;
        this.startCardTypeId = startCardTypeId;
        this.content = content;
        this.mediaIds = List.copyOf(mediaIds);
        this.cardTypeIds = List.copyOf(cardTypeIds);
        this.publishedAt = publishedAt;
        this.publishedBy = publishedBy;
    }
}
