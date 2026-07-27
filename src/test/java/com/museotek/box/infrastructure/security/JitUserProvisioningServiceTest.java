package com.museotek.box.infrastructure.security;

import com.museotek.box.domain.user.User;
import com.museotek.box.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JitUserProvisioningServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final JitUserProvisioningService service = new JitUserProvisioningService(userRepository);

    @BeforeEach
    void stubSaveAndFlush() {
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Jwt jwt(String subject, String email, String preferredUsername, String name) {
        var builder = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(subject)
                .claim("aud", List.of("uniche-platform"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (email != null) builder.claim("email", email);
        if (preferredUsername != null) builder.claim("preferred_username", preferredUsername);
        if (name != null) builder.claim("name", name);
        return builder.build();
    }

    @Test
    void newSubject_createsUserWithFirstSeenAtSet() {
        when(userRepository.findBySubject("user-1")).thenReturn(Optional.empty());

        service.provision(jwt("user-1", "a@example.com", "alice", "Alice"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getSubject()).isEqualTo("user-1");
        assertThat(saved.getEmail()).isEqualTo("a@example.com");
        assertThat(saved.getPreferredUsername()).isEqualTo("alice");
        assertThat(saved.getDisplayName()).isEqualTo("Alice");
        assertThat(saved.getFirstSeenAt()).isNotNull();
        assertThat(saved.getLastSeenAt()).isNotNull();
    }

    @Test
    void existingSubject_updatesFieldsButPreservesFirstSeenAt() {
        Instant originalFirstSeen = Instant.parse("2026-01-01T00:00:00Z");
        User existing = new User();
        existing.setSubject("user-1");
        existing.setEmail("old@example.com");
        existing.setFirstSeenAt(originalFirstSeen);
        when(userRepository.findBySubject("user-1")).thenReturn(Optional.of(existing));

        service.provision(jwt("user-1", "new@example.com", "alice2", "Alice Renamed"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).saveAndFlush(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("new@example.com");
        assertThat(saved.getPreferredUsername()).isEqualTo("alice2");
        assertThat(saved.getDisplayName()).isEqualTo("Alice Renamed");
        assertThat(saved.getFirstSeenAt()).isEqualTo(originalFirstSeen);
    }

    @Test
    void blankSubject_doesNothing() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("")
                .claim("aud", List.of("uniche-platform"))
                .build();

        service.provision(jwt);

        verifyNoInteractions(userRepository);
    }

    @Test
    void concurrentFirstRequest_fallsBackToUpdatingWinnerRowOnUniqueConstraintViolation() {
        when(userRepository.findBySubject("user-1"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingRowCreatedByConcurrentWinner()));
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"))
                .thenAnswer(inv -> inv.getArgument(0));

        service.provision(jwt("user-1", "a@example.com", "alice", "Alice"));

        verify(userRepository, times(2)).saveAndFlush(any(User.class));
        verify(userRepository, times(2)).findBySubject("user-1");
    }

    @Test
    void concurrentFirstRequest_secondSaveFailureIsNotSwallowedSilently() {
        // Sanity check that we don't retry forever or hide a genuinely broken DB:
        // only one fallback attempt is made after the first constraint violation.
        when(userRepository.findBySubject("user-1")).thenReturn(Optional.empty());
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        service.provision(jwt("user-1", "a@example.com", "alice", "Alice"));

        verify(userRepository, times(1)).saveAndFlush(any(User.class));
        verify(userRepository, never()).save(any(User.class));
    }

    private User existingRowCreatedByConcurrentWinner() {
        User user = new User();
        user.setSubject("user-1");
        user.setFirstSeenAt(Instant.now());
        return user;
    }
}
