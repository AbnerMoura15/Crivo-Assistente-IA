package com.ragbot.application.service;

import com.ragbot.application.port.in.ManageUsersUseCase;
import com.ragbot.application.port.out.UserRepositoryPort;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import com.ragbot.domain.exception.DocumentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService implements ManageUsersUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepositoryPort userRepository;

    public UserService(UserRepositoryPort userRepository) {
        this.userRepository = userRepository;
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
