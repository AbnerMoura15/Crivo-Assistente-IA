package com.ragbot.adapter.in.rest;

import com.ragbot.application.port.in.AuthUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoint público de autenticação (login).
 * Não há auto-cadastro: usuários são criados por um ADMIN via POST /api/users.
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

    // ── DTOs ────────────────────────────────────────────────────────────────

    record LoginRequest(
            @NotBlank(message = "Username é obrigatório") @Size(max = 50) String username,
            @NotBlank(message = "Senha é obrigatória") @Size(max = 72) String password
    ) {}

    record LoginResponse(String token) {}
}
