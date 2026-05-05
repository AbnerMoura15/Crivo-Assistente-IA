package com.ragbot.adapter.in.rest;

import com.ragbot.application.port.in.ManageUsersUseCase;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/**
 * Gerenciamento de usuários — acesso restrito a ADMIN.
 * A anotação @PreAuthorize garante no nível do método,
 * além da regra já definida no SecurityConfig.
 */
@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final ManageUsersUseCase manageUsersUseCase;

    public UserController(ManageUsersUseCase manageUsersUseCase) {
        this.manageUsersUseCase = manageUsersUseCase;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listAll() {
        List<UserResponse> users = manageUsersUseCase.listAll()
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(UserResponse.from(manageUsersUseCase.findById(id)));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable String id,
            @RequestBody ChangeRoleRequest request) {
        User updated = manageUsersUseCase.changeRole(id, request.role());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String id) {
        manageUsersUseCase.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable String id) {
        manageUsersUseCase.activate(id);
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    record UserResponse(
            String id, String username, String email,
            String role, boolean active, Instant createdAt, Instant lastLogin
    ) {
        static UserResponse from(User u) {
            return new UserResponse(u.id(), u.username(), u.email(),
                    u.role().name(), u.active(), u.createdAt(), u.lastLogin());
        }
    }

    record ChangeRoleRequest(UserRole role) {}
}
