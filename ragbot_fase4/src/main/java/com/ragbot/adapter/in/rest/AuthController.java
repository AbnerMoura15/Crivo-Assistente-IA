package com.ragbot.adapter.in.rest;

import com.ragbot.application.port.in.AuthUseCase;
import com.ragbot.domain.entity.UserRole;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints públicos de autenticação.
 * Não exigem JWT — são o ponto de entrada para obter o token.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    /** POST /api/auth/login — retorna JWT */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authUseCase.login(request.username(), request.password());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    /** POST /api/auth/register — cria novo usuário (apenas ADMIN deveria chamar em produção) */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        UserRole role = request.role() != null ? request.role() : UserRole.VIEWER;
        authUseCase.register(request.username(), request.email(), request.password(), role);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    record LoginRequest(
            @NotBlank(message = "Username é obrigatório") String username,
            @NotBlank(message = "Senha é obrigatória") String password
    ) {}

    record LoginResponse(String token) {}

    record RegisterRequest(
            @NotBlank(message = "Username é obrigatório")
            @Size(min = 3, max = 50) String username,
            @NotBlank @Email(message = "Email inválido") String email,
            @NotBlank @Size(min = 6, message = "Senha deve ter no mínimo 6 caracteres") String password,
            UserRole role
    ) {}
}
