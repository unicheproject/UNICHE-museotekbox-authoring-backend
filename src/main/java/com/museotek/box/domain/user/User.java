package com.museotek.box.domain.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String subject;

    @Column
    private String email;

    @Column
    private String preferredUsername;

    @Column
    private String displayName;

    @Column(nullable = false, updatable = false)
    private Instant firstSeenAt;

    @Column
    private Instant lastSeenAt;
}
