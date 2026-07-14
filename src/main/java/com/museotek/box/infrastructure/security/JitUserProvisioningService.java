package com.museotek.box.infrastructure.security;

import com.museotek.box.domain.user.User;
import com.museotek.box.infrastructure.repository.UserRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class JitUserProvisioningService {

    private final UserRepository userRepository;

    public JitUserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
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
        userRepository.save(user);
    }
}
