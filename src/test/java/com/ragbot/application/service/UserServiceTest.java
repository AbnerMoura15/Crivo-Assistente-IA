package com.ragbot.application.service;

import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import com.ragbot.domain.exception.DocumentNotFoundException;
import com.ragbot.domain.exception.UserAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * TDD — UserService.
 * Cobre: listagem, busca por ID, alteração de role e desativação.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

    @Mock
    private UserRepositoryPort userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // custo baixo no teste
    private UserService userService;

    private User viewer;
    private User editor;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
        viewer = new User("id-1", "jose", "jose@fab.com", "hash", UserRole.VIEWER, true, Instant.now(), null);
        editor = new User("id-2", "maria", "maria@fab.com", "hash", UserRole.EDITOR, true, Instant.now(), null);
    }

    @Test
    @DisplayName("listAll deve retornar todos os usuários")
    void listAllShouldReturnAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(viewer, editor));
        List<User> users = userService.listAll();
        assertThat(users).hasSize(2).containsExactly(viewer, editor);
    }

    @Test
    @DisplayName("findById deve retornar usuário existente")
    void findByIdShouldReturnUser() {
        when(userRepository.findById("id-1")).thenReturn(Optional.of(viewer));
        User found = userService.findById("id-1");
        assertThat(found.username()).isEqualTo("jose");
    }

    @Test
    @DisplayName("findById deve lançar exceção para ID inexistente")
    void findByIdShouldThrowWhenNotFound() {
        when(userRepository.findById("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> userService.findById("ghost"))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    @Test
    @DisplayName("changeRole deve atualizar o perfil do usuário")
    void changeRoleShouldUpdateUserRole() {
        when(userRepository.findById("id-1")).thenReturn(Optional.of(viewer));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.changeRole("id-1", UserRole.EDITOR);

        assertThat(updated.role()).isEqualTo(UserRole.EDITOR);
        verify(userRepository).save(argThat(u -> u.role() == UserRole.EDITOR));
    }

    @Test
    @DisplayName("deactivate deve marcar usuário como inativo")
    void deactivateShouldMarkUserInactive() {
        when(userRepository.findById("id-2")).thenReturn(Optional.of(editor));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.deactivate("id-2");

        verify(userRepository).save(argThat(u -> !u.active()));
    }

    @Test
    @DisplayName("activate deve reativar usuário inativo")
    void activateShouldMarkUserActive() {
        User inactive = new User("id-3", "pedro", "pedro@fab.com", "hash", UserRole.VIEWER, false, Instant.now(), null);
        when(userRepository.findById("id-3")).thenReturn(Optional.of(inactive));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        userService.activate("id-3");

        verify(userRepository).save(argThat(User::active));
    }

    @Test
    @DisplayName("createUser deve salvar usuário com senha hasheada e retornar o criado")
    void createUserShouldSaveWithHashedPassword() {
        when(userRepository.existsByUsername("novo")).thenReturn(false);
        when(userRepository.existsByEmail("novo@fab.com")).thenReturn(false);
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        User created = userService.createUser("novo", "novo@fab.com", "senha123", UserRole.VIEWER);

        assertThat(created.username()).isEqualTo("novo");
        assertThat(created.role()).isEqualTo(UserRole.VIEWER);
        assertThat(created.active()).isTrue();
        assertThat(created.passwordHash()).isNotEqualTo("senha123"); // nunca armazenar em texto puro
        assertThat(passwordEncoder.matches("senha123", created.passwordHash())).isTrue();
    }

    @Test
    @DisplayName("createUser deve lançar exceção se username já existe")
    void createUserShouldThrowWhenUsernameExists() {
        when(userRepository.existsByUsername("jose")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("jose", "outro@fab.com", "senha123", UserRole.VIEWER))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("jose");
    }

    @Test
    @DisplayName("createUser deve lançar exceção se email já existe")
    void createUserShouldThrowWhenEmailExists() {
        when(userRepository.existsByUsername("novouser")).thenReturn(false);
        when(userRepository.existsByEmail("jose@fab.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.createUser("novouser", "jose@fab.com", "senha123", UserRole.EDITOR))
                .isInstanceOf(UserAlreadyExistsException.class)
                .hasMessageContaining("jose@fab.com");
    }
}
