package com.ragbot.application.service;

import com.ragbot.application.port.out.AuditLogPort;
import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.config.security.JwtTokenProvider;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import com.ragbot.domain.exception.InvalidCredentialsException;
import com.ragbot.domain.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService")
class AuthServiceTest {

    @Mock private UserRepositoryPort userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuditLogPort auditLogPort;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    private User adminUser;
    private static final String RAW_PASSWORD = "senha123";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        AuditLogService auditLogService = new AuditLogService(auditLogPort);
        authService = new AuthService(userRepository, jwtTokenProvider, passwordEncoder, auditLogService);

        adminUser = new User(
                "uuid-admin", "admin", "admin@ragbot.com",
                passwordEncoder.encode(RAW_PASSWORD),
                UserRole.ADMIN, true, Instant.now(), null
        );
    }

    @Test
    @DisplayName("login com credenciais válidas deve retornar JWT")
    void loginWithValidCredentialsShouldReturnToken() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(jwtTokenProvider.generateToken("admin", "ADMIN")).thenReturn("jwt-token-mock");

        String token = authService.login("admin", RAW_PASSWORD);

        assertThat(token).isEqualTo("jwt-token-mock");
        verify(userRepository).findByUsername("admin");
        verify(jwtTokenProvider).generateToken("admin", "ADMIN");
    }

    @Test
    @DisplayName("login com senha errada deve lançar InvalidCredentialsException e registrar falha")
    void loginWithWrongPasswordShouldThrow() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> authService.login("admin", "senha-errada"))
                .isInstanceOf(InvalidCredentialsException.class);

        verify(jwtTokenProvider, never()).generateToken(any(), any());
        verify(auditLogPort).save(argThat(e -> e.action().name().equals("LOGIN_FAILED")));
    }

    @Test
    @DisplayName("login com usuário inexistente deve lançar InvalidCredentialsException")
    void loginWithUnknownUserShouldThrow() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login("ghost", RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login com usuário inativo deve lançar InvalidCredentialsException")
    void loginWithInactiveUserShouldThrow() {
        User inactiveUser = new User(
                "uuid-inactive", "inativo", "inativo@ragbot.com",
                passwordEncoder.encode(RAW_PASSWORD),
                UserRole.VIEWER, false, Instant.now(), null
        );
        when(userRepository.findByUsername("inativo")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> authService.login("inativo", RAW_PASSWORD))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("inativo");
    }

    @Test
    @DisplayName("login bem-sucedido deve registrar LOGIN_SUCCESS no audit log")
    void loginSuccessShouldAudit() {
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(jwtTokenProvider.generateToken(any(), any())).thenReturn("token");

        authService.login("admin", RAW_PASSWORD);

        verify(auditLogPort).save(argThat(e -> e.action().name().equals("LOGIN_SUCCESS")));
    }

    @Test
    @DisplayName("registro de novo usuário deve salvar com senha criptografada")
    void registerNewUserShouldSaveWithHashedPassword() {
        when(userRepository.existsByUsername("novo")).thenReturn(false);
        when(userRepository.existsByEmail("novo@fab.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        authService.register("novo", "novo@fab.com", RAW_PASSWORD, UserRole.VIEWER);

        verify(userRepository).save(argThat(user -> {
            assertThat(user.passwordHash()).isNotEqualTo(RAW_PASSWORD);
            assertThat(passwordEncoder.matches(RAW_PASSWORD, user.passwordHash())).isTrue();
            assertThat(user.username()).isEqualTo("novo");
            assertThat(user.active()).isTrue();
            return true;
        }));
    }

    @Test
    @DisplayName("registro com username duplicado deve lançar UserAlreadyExistsException")
    void registerWithDuplicateUsernameShouldThrow() {
        when(userRepository.existsByUsername("admin")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("admin", "outro@fab.com", RAW_PASSWORD, UserRole.VIEWER))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("username");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("registro com email duplicado deve lançar UserAlreadyExistsException")
    void registerWithDuplicateEmailShouldThrow() {
        when(userRepository.existsByUsername("novouser")).thenReturn(false);
        when(userRepository.existsByEmail("admin@ragbot.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register("novouser", "admin@ragbot.com", RAW_PASSWORD, UserRole.VIEWER))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("email");

        verify(userRepository, never()).save(any());
    }
}
