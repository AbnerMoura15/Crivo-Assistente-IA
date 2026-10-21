package com.ragbot.domain.entity;

import java.time.Instant;

/**
 * Entidade de domínio que representa um usuário do sistema.
 * Imutável por ser um record — para alterar, cria-se uma nova instância.
 */
public record User(
        String id,
        String username,
        String email,
        String passwordHash,   // NUNCA armazenar senha em texto puro
        UserRole role,
        boolean active,
        Instant createdAt,
        Instant lastLogin      // pode ser null (nunca logou)
) {
    /** Cria uma cópia com role alterada */
    public User withRole(UserRole newRole) {
        return new User(id, username, email, passwordHash, newRole, active, createdAt, lastLogin);
    }

    /** Cria uma cópia com status ativo alterado */
    public User withActive(boolean newActive) {
        return new User(id, username, email, passwordHash, role, newActive, createdAt, lastLogin);
    }

    /** Cria uma cópia com novo hash de senha */
    public User withPasswordHash(String newPasswordHash) {
        return new User(id, username, email, newPasswordHash, role, active, createdAt, lastLogin);
    }

    /** Cria uma cópia com lastLogin atualizado */
    public User withLastLogin(Instant loginTime) {
        return new User(id, username, email, passwordHash, role, active, createdAt, loginTime);
    }
}
