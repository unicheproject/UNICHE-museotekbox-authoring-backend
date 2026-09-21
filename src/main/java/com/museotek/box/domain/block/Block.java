package com.museotek.box.domain.block;

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

@Entity
@Table(name = "blocks")
@Getter
@Setter
@NoArgsConstructor
public class Block {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @Column(nullable = false)
    private String blockKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BlockType type;

    @Column(nullable = false)
    private Integer position;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String content;
}
