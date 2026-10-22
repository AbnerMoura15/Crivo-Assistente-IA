package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.application.port.in.ManageUsersUseCase;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.TokenUsageService;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final ManageUsersUseCase manageUsersUseCase;
    private final AuditLogService auditLogService;
    private final TokenUsageService tokenUsageService;

    public UserController(ManageUsersUseCase manageUsersUseCase,
                          AuditLogService auditLogService,
                          TokenUsageService tokenUsageService) {
        this.manageUsersUseCase = manageUsersUseCase;
        this.auditLogService = auditLogService;
        this.tokenUsageService = tokenUsageService;
    }

    /** Uso de tokens do próprio usuário — acessível por qualquer papel autenticado. */
    @GetMapping("/me/usage")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RestDtos.TokenUsageResponse> getMyUsage(Authentication auth) {
        long used  = tokenUsageService.getCurrentMonthUsage(auth.getName());
        long limit = tokenUsageService.getMonthlyLimit();
        return ResponseEntity.ok(new RestDtos.TokenUsageResponse(auth.getName(), used, limit));
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> listAll() {
        return ResponseEntity.ok(
                manageUsersUseCase.listAll().stream().map(UserResponse::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> findById(@PathVariable String id) {
        return ResponseEntity.ok(UserResponse.from(manageUsersUseCase.findById(id)));
    }

    /** Cria usuário diretamente pelo admin (sem passar pelo fluxo público de registro). */
    @PostMapping
    public ResponseEntity<UserResponse> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        User created = manageUsersUseCase.createUser(
                request.username(), request.email(), request.password(), request.role());
        auditLogService.record(auth.getName(), AuditAction.USER_CREATE, created.id(), "USER",
                "username=" + created.username() + " role=" + created.role().name(),
                httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(created));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponse> changeRole(
            @PathVariable String id,
            @RequestBody ChangeRoleRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        User updated = manageUsersUseCase.changeRole(id, request.role());
        auditLogService.record(auth.getName(), AuditAction.ROLE_CHANGE, id, "USER",
                "novoRole=" + request.role().name(), httpRequest.getRemoteAddr());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<Void> deactivate(@PathVariable String id, Authentication auth,
                                           HttpServletRequest httpRequest) {
        manageUsersUseCase.deactivate(id);
        auditLogService.record(auth.getName(), AuditAction.USER_DEACTIVATE, id, "USER",
                null, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<Void> activate(@PathVariable String id, Authentication auth,
                                         HttpServletRequest httpRequest) {
        manageUsersUseCase.activate(id);
        auditLogService.record(auth.getName(), AuditAction.USER_ACTIVATE, id, "USER",
                null, httpRequest.getRemoteAddr());
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

    record CreateUserRequest(
            @NotBlank String username,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, message = "Senha deve ter ao menos 6 caracteres") String password,
            UserRole role
    ) {}

    record ChangeRoleRequest(UserRole role) {}
}
