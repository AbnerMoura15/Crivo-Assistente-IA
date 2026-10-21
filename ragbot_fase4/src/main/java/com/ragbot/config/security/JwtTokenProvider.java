package com.ragbot.config.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Gera e valida tokens JWT.
 *
 * O token carrega:
 *  - subject: username
 *  - claim "role": perfil do usuário (VIEWER, EDITOR, ADMIN)
 *  - iat: data de emissão
 *  - exp: data de expiração
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    /** HS256 exige chave de pelo menos 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${ragbot.security.jwt.secret}") String secret,
            @Value("${ragbot.security.jwt.expiration-seconds:86400}") long expirationSeconds) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET ausente ou fraco: mínimo " + MIN_SECRET_BYTES + " bytes (gere com: openssl rand -base64 48)");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;
    }

    /** Gera um JWT assinado com username e role */
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /** Extrai o username (subject) do token */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    /** Extrai o perfil (role) do token */
    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /** Retorna true se o token for válido e não expirado */
    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
