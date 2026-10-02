package com.museotek.box.domain.media;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One file in an organisation's media library: a picture, video or sound a curator uploaded
 * to use in experiences. The bytes live in {@code MediaStorage} under {@link #storedFilename};
 * {@link #name} is only what the picker shows (the original filename by default).
 */
@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
public class Media {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID orgId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaKind kind;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    @Column(nullable = false)
    private String storedFilename;

    // The uploader's JWT subject, like users.subject.
    @Column
    private String uploadedBy;

    @Column(nullable = false)
    private Instant uploadedAt;
}
