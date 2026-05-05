package com.ragbot.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD — testes escritos ANTES da implementação do JwtTokenProvider.
 * Define o contrato exato que a implementação deve satisfazer.
 */
@DisplayName("JwtTokenProvider")
class JwtTokenProviderTest {

    private JwtTokenProvider provider;

    // Segredo mínimo de 32 chars para HMAC-SHA256
    private static final String SECRET = "test-secret-key-must-be-256-bits!!";

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider(SECRET, 3600L);
    }

    @Test
    @DisplayName("deve gerar token não nulo")
    void shouldGenerateNonNullToken() {
        String token = provider.generateToken("user1", "VIEWER");
        assertThat(token).isNotNull().isNotBlank();
    }

    @Test
    @DisplayName("deve extrair username do token")
    void shouldExtractUsernameFromToken() {
        String token = provider.generateToken("joao.silva", "EDITOR");
        assertThat(provider.extractUsername(token)).isEqualTo("joao.silva");
    }

    @Test
    @DisplayName("deve extrair role do token")
    void shouldExtractRoleFromToken() {
        String token = provider.generateToken("admin", "ADMIN");
        assertThat(provider.extractRole(token)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("deve validar token válido")
    void shouldValidateValidToken() {
        String token = provider.generateToken("user1", "VIEWER");
        assertThat(provider.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("deve rejeitar token adulterado")
    void shouldRejectTamperedToken() {
        String token = provider.generateToken("user1", "VIEWER");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertThat(provider.isTokenValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar token expirado")
    void shouldRejectExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(SECRET, 1L);
        String token = shortLived.generateToken("user1", "VIEWER");
        Thread.sleep(1500);
        assertThat(shortLived.isTokenValid(token)).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar token nulo")
    void shouldRejectNullToken() {
        assertThat(provider.isTokenValid(null)).isFalse();
    }

    @Test
    @DisplayName("deve rejeitar token vazio")
    void shouldRejectEmptyToken() {
        assertThat(provider.isTokenValid("")).isFalse();
    }

    @Test
    @DisplayName("usuários diferentes devem ter tokens diferentes")
    void differentUsersShouldHaveDifferentTokens() {
        String t1 = provider.generateToken("user1", "VIEWER");
        String t2 = provider.generateToken("user2", "VIEWER");
        assertThat(t1).isNotEqualTo(t2);
    }
}
