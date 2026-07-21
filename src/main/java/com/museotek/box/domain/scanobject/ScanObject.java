package com.museotek.box.domain.scanobject;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "scan_objects")
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(name = "kind")
@Getter
@Setter
@NoArgsConstructor
public abstract class ScanObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID orgId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scan_object_type_id")
    private ScanObjectType scanObjectType;

    @Column(nullable = false)
    private String name;

    // Nullable: a Draft is digital-only and may have no physical tag assigned yet.
    @Column(unique = true)
    private String rfidTag;

    @Column(nullable = false)
    private boolean reusable;
}
