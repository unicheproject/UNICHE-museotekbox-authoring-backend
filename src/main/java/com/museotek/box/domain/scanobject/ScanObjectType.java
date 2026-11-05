package com.museotek.box.domain.scanobject;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "scan_object_types")
@Getter
@Setter
@NoArgsConstructor
public class ScanObjectType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private UUID orgId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    // Works in any experience (Right/Wrong, answers A/B/C); what it does depends on the current scene's rules.
    @Column(name = "universal", nullable = false)
    private boolean universal;

    // The org's Exit card: ends any visit, in any scene. At most one per org.
    @Column(name = "is_exit", nullable = false)
    private boolean exit;
}
