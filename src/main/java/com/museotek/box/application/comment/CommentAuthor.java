package com.museotek.box.application.comment;

import com.museotek.box.domain.user.User;
import com.museotek.box.infrastructure.repository.UserRepository;
import com.museotek.box.infrastructure.security.CurrentPrincipal;
import org.springframework.stereotype.Component;

/**
 * Who is writing: the caller's JWT subject and the name to show next to their comment. The name
 * comes from the local users row the JIT provisioning filter keeps up to date: display name, else
 * username, else email, else the subject itself.
 */
@Component
public class CommentAuthor {

    public record Author(String subject, String name) {
    }

    private final UserRepository userRepository;

    public CommentAuthor(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Author current() {
        String subject = CurrentPrincipal.subject()
                .orElseThrow(() -> new IllegalStateException("No JWT in security context"));
        User user = userRepository.findBySubject(subject).orElse(null);
        return new Author(subject, nameOf(user, subject));
    }

    private static String nameOf(User user, String subject) {
        if (user == null) {
            return subject;
        }
        if (!isBlank(user.getDisplayName())) {
            return user.getDisplayName();
        }
        if (!isBlank(user.getPreferredUsername())) {
            return user.getPreferredUsername();
        }
        if (!isBlank(user.getEmail())) {
            return user.getEmail();
        }
        return subject;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
