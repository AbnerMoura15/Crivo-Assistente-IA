package com.ragbot.application.port.in;

import com.ragbot.domain.entity.UserRole;

public interface AuthUseCase {
    String login(String username, String password);
    void register(String username, String email, String password, UserRole role);
}
