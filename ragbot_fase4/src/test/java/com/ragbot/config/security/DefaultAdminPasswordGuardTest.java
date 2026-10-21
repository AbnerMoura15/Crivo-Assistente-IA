package com.ragbot.config.security;

import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("DefaultAdminPasswordGuard — senha padrão do admin do seed")
class DefaultAdminPasswordGuardTest {

    /** Hash exato gravado por V3__create_users.sql. */
    private static final String SEED_HASH = "$2a$12$/WCz2rD9qXs0kh7gKiVoFe7he4jKdRxcgBBOxnLIBR1mQjXp15Ru2";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final UserRepositoryPort repo = mock(UserRepositoryPort.class);

    private User admin(String hash) {
        return new User("1", "admin", "admin@ragbot.local", hash, UserRole.ADMIN, true, Instant.now(), null);
    }

    @Test
    @DisplayName("o hash do seed V3 corresponde mesmo à senha conhecida (o guard detecta o caso real)")
    void seedHashMatchesKnownPassword() {
        assertThat(encoder.matches(DefaultAdminPasswordGuard.SEEDED_PASSWORD, SEED_HASH)).isTrue();
    }

    @Test
    @DisplayName("prod sem RAGBOT_ADMIN_PASSWORD e admin com senha do seed → startup falha")
    void failsInProdWithDefaultPassword() {
        when(repo.findByUsername("admin")).thenReturn(Optional.of(admin(SEED_HASH)));
        var guard = new DefaultAdminPasswordGuard(repo, encoder, "", true);

        assertThatThrownBy(() -> guard.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAGBOT_ADMIN_PASSWORD");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("RAGBOT_ADMIN_PASSWORD definida → senha do admin é trocada")
    void replacesDefaultPassword() {
        when(repo.findByUsername("admin")).thenReturn(Optional.of(admin(SEED_HASH)));
        var guard = new DefaultAdminPasswordGuard(repo, encoder, "uma-senha-forte-123", true);

        guard.run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(repo).save(saved.capture());
        assertThat(encoder.matches("uma-senha-forte-123", saved.getValue().passwordHash())).isTrue();
        assertThat(encoder.matches(DefaultAdminPasswordGuard.SEEDED_PASSWORD, saved.getValue().passwordHash())).isFalse();
    }

    @Test
    @DisplayName("RAGBOT_ADMIN_PASSWORD curta é recusada")
    void rejectsShortReplacement() {
        when(repo.findByUsername("admin")).thenReturn(Optional.of(admin(SEED_HASH)));
        var guard = new DefaultAdminPasswordGuard(repo, encoder, "curta", true);

        assertThatThrownBy(() -> guard.run(null)).isInstanceOf(IllegalStateException.class);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("admin com senha já trocada → nada acontece, mesmo em prod")
    void ignoresAlreadyChangedPassword() {
        when(repo.findByUsername("admin")).thenReturn(Optional.of(admin(encoder.encode("outra-senha-qualquer"))));
        var guard = new DefaultAdminPasswordGuard(repo, encoder, "", true);

        guard.run(null);

        verify(repo, never()).save(any());
    }
}
