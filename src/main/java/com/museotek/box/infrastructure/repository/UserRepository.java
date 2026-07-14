package com.museotek.box.infrastructure.repository;

import com.museotek.box.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findBySubject(String subject);
}
