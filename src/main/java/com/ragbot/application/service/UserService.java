package com.ragbot.application.service;

import com.ragbot.application.port.in.ManageUsersUseCase;
import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import com.ragbot.domain.exception.DocumentNotFoundException;
import com.ragbot.domain.exception.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class UserService implements ManageUsersUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepositoryPort userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public List<User> listAll() {
        return userRepository.findAll();
    }

    @Override
    public User findById(String id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
    }

    @Override
    public User createUser(String username, String email, String plainPassword, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("usuário", username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("e-mail", email);
        }
        User user = new User(
                UUID.randomUUID().toString(),
                username,
                email,
                passwordEncoder.encode(plainPassword),
                role,
                true,
                Instant.now(),
                null
        );
        User saved = userRepository.save(user);
        log.info("Usuário criado pelo admin: {} ({})", username, role);
        return saved;
    }

    @Override
    public User changeRole(String userId, UserRole newRole) {
        User user = findById(userId);
        User updated = user.withRole(newRole);
        userRepository.save(updated);
        log.info("Role alterada: {} → {}", user.username(), newRole);
        return updated;
    }

    @Override
    public void deactivate(String userId) {
        User user = findById(userId);
        userRepository.save(user.withActive(false));
        log.info("Usuário desativado: {}", user.username());
    }

    @Override
    public void activate(String userId) {
        User user = findById(userId);
        userRepository.save(user.withActive(true));
        log.info("Usuário reativado: {}", user.username());
    }
}
