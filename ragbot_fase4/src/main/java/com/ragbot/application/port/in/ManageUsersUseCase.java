package com.ragbot.application.port.in;

import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;

import java.util.List;

public interface ManageUsersUseCase {
    List<User> listAll();
    User findById(String id);
    User createUser(String username, String email, String plainPassword, UserRole role);
    User changeRole(String userId, UserRole newRole);
    void deactivate(String userId);
    void activate(String userId);
}
