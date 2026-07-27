package com.museotek.box.infrastructure.security;

import com.museotek.box.domain.user.User;
import com.museotek.box.infrastructure.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class JitUserProvisioningService {

    private final UserRepository userRepository;

    public JitUserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Deliberately NOT @Transactional: each repository call below already gets its own
    // transaction (Spring Data JPA repository methods are transactional per-call). That
    // is required, not incidental — if the initial saveAndFlush below loses a race and
    // throws a constraint violation, Hibernate marks that session unusable for any
    // further flush; wrapping this method in one outer transaction would make the
    // fallback save fail too ("don't flush the Session after an exception occurs").
    // Keeping the two attempts in separate transactions lets the fallback succeed clean.
    public void provision(Jwt jwt) {
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) return;

        String email = jwt.getClaimAsString("email");
        String preferredUsername = jwt.getClaimAsString("preferred_username");
        String displayName = jwt.getClaimAsString("name");
        Instant now = Instant.now();

        User user = userRepository.findBySubject(subject).orElseGet(() -> {
            User u = new User();
            u.setSubject(subject);
            u.setFirstSeenAt(now);
            return u;
        });
        user.setEmail(email);
        user.setPreferredUsername(preferredUsername);
        user.setDisplayName(displayName);
        user.setLastSeenAt(now);
        try {
            // saveAndFlush (not save) so a unique-constraint violation surfaces here,
            // inside this try block, rather than later at transaction commit.
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Lost a race against a concurrent first request for the same brand-new
            // subject: the other request's insert won, so fall back to updating the
            // row it just created instead of failing this request.
            userRepository.findBySubject(subject).ifPresent(existing -> {
                existing.setEmail(email);
                existing.setPreferredUsername(preferredUsername);
                existing.setDisplayName(displayName);
                existing.setLastSeenAt(now);
                userRepository.saveAndFlush(existing);
            });
        }
    }
}
