package com.ragbot.application.port.out;

import com.ragbot.domain.entity.User;

import java.util.List;
import java.util.Optional;

/**
 * Port de saída para persistência de usuários.
 * Implementado por: UserRepositoryAdapter (JDBC + PostgreSQL)
 */
public interface UserRepositoryPort {
    User save(User user);
    Optional<User> findById(String id);
    Optional<User> findByUsername(String username);
    List<User> findAll();
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    void updateLastLogin(String userId);
}
