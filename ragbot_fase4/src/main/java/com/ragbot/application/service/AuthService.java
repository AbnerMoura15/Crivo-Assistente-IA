package com.ragbot.application.service;

import com.ragbot.application.port.in.AuthUseCase;
import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.config.security.JwtTokenProvider;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import com.ragbot.domain.exception.InvalidCredentialsException;
import com.ragbot.domain.exception.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService implements AuthUseCase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepositoryPort userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepositoryPort userRepository,
                       JwtTokenProvider jwtTokenProvider,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public String login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(InvalidCredentialsException::new);

        if (!user.active()) {
            throw new InvalidCredentialsException("Usuário inativo. Contate o administrador.");
        }

        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        userRepository.updateLastLogin(user.id());
        log.info("Login bem-sucedido: {} ({})", username, user.role());

        return jwtTokenProvider.generateToken(user.username(), user.role().name());
    }

    @Override
    public void register(String username, String email, String password, UserRole role) {
        if (userRepository.existsByUsername(username)) {
            throw new UserAlreadyExistsException("username", username);
        }
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException("email", email);
        }

        User newUser = new User(
                UUID.randomUUID().toString(),
                username,
                email,
                passwordEncoder.encode(password),   // senha NUNCA em texto puro
                role,
                true,
                Instant.now(),
                null
        );

        userRepository.save(newUser);
        log.info("Novo usuário registrado: {} ({})", username, role);
    }
}
