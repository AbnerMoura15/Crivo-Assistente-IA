package com.ragbot.config.security;

import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.domain.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * A migration V3 cria o usuário "admin" com uma senha conhecida (publicada no repositório).
 * No startup, se esse admin ainda usar a senha do seed:
 *  - RAGBOT_ADMIN_PASSWORD definida → troca a senha por ela;
 *  - senão, com fail-on-default-admin-password=true (prod) → impede o startup;
 *  - senão (dev) → apenas avisa no log.
 */
@Component
public class DefaultAdminPasswordGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DefaultAdminPasswordGuard.class);

    static final String SEEDED_USERNAME = "admin";
    static final String SEEDED_PASSWORD = "Admin@123"; // seed de V3__create_users.sql
    static final int MIN_PASSWORD_LENGTH = 12;

    private final UserRepositoryPort userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String newAdminPassword;
    private final boolean failOnDefaultPassword;

    public DefaultAdminPasswordGuard(
            UserRepositoryPort userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${ragbot.security.admin-password:}") String newAdminPassword,
            @Value("${ragbot.security.fail-on-default-admin-password:false}") boolean failOnDefaultPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.newAdminPassword = newAdminPassword;
        this.failOnDefaultPassword = failOnDefaultPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByUsername(SEEDED_USERNAME).orElse(null);
        if (admin == null || !admin.active()
                || !passwordEncoder.matches(SEEDED_PASSWORD, admin.passwordHash())) {
            return;
        }

        if (newAdminPassword != null && !newAdminPassword.isBlank()) {
            if (newAdminPassword.length() < MIN_PASSWORD_LENGTH || newAdminPassword.equals(SEEDED_PASSWORD)) {
                throw new IllegalStateException(
                        "RAGBOT_ADMIN_PASSWORD deve ter no mínimo " + MIN_PASSWORD_LENGTH + " caracteres");
            }
            userRepository.save(admin.withPasswordHash(passwordEncoder.encode(newAdminPassword)));
            log.warn("Senha padrão do usuário 'admin' substituída via RAGBOT_ADMIN_PASSWORD");
            return;
        }

        if (failOnDefaultPassword) {
            throw new IllegalStateException(
                    "Usuário 'admin' ainda usa a senha padrão do seed. Defina RAGBOT_ADMIN_PASSWORD para trocá-la.");
        }
        log.warn("Usuário 'admin' usa a senha padrão do seed — defina RAGBOT_ADMIN_PASSWORD antes de publicar");
    }
}
