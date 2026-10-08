package com.museotek.box.application.comment;

import com.museotek.box.domain.user.User;
import com.museotek.box.infrastructure.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentAuthorTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CommentAuthor commentAuthor = new CommentAuthor(userRepository);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void prefersDisplayName_thenUsername_thenEmail_thenSubject() {
        signIn("user-1");

        when(userRepository.findBySubject("user-1")).thenReturn(Optional.of(user("Maria P.", "maria", "maria@museum.gr")));
        assertThat(commentAuthor.current().name()).isEqualTo("Maria P.");

        when(userRepository.findBySubject("user-1")).thenReturn(Optional.of(user(" ", "maria", "maria@museum.gr")));
        assertThat(commentAuthor.current().name()).isEqualTo("maria");

        when(userRepository.findBySubject("user-1")).thenReturn(Optional.of(user(null, null, "maria@museum.gr")));
        assertThat(commentAuthor.current().name()).isEqualTo("maria@museum.gr");

        when(userRepository.findBySubject("user-1")).thenReturn(Optional.empty());
        assertThat(commentAuthor.current()).isEqualTo(new CommentAuthor.Author("user-1", "user-1"));
    }

    static void signIn(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private static User user(String displayName, String username, String email) {
        User user = new User();
        user.setDisplayName(displayName);
        user.setPreferredUsername(username);
        user.setEmail(email);
        return user;
    }
}
