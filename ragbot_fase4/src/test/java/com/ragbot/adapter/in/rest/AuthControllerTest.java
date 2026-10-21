package com.ragbot.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.service.AuthService;
import com.ragbot.domain.exception.InvalidCredentialsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * TDD — AuthController via MockMvc.
 * Testa os endpoints de login e registro sem subir o servidor completo.
 */
@WebMvcTest(AuthController.class)
@Import(TestSecurityConfig.class)
@DisplayName("AuthController")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login com credenciais válidas retorna 200 e token")
    void loginWithValidCredentialsReturns200() throws Exception {
        when(authService.login("admin", "senha123")).thenReturn("jwt-token-gerado");

        String body = """
                {"username": "admin", "password": "senha123"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token-gerado"));
    }

    @Test
    @DisplayName("POST /api/auth/login com credenciais inválidas retorna 401")
    void loginWithInvalidCredentialsReturns401() throws Exception {
        when(authService.login(any(), any())).thenThrow(new InvalidCredentialsException());

        String body = """
                {"username": "admin", "password": "errada"}
                """;

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/auth/login com body vazio retorna 400")
    void loginWithEmptyBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/auth/register ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/register não existe — ninguém se cadastra (nem como ADMIN)")
    void publicRegisterIsNotAvailable() throws Exception {
        String body = """
                {"username": "invasor", "email": "x@x.com", "password": "senha123", "role": "ADMIN"}
                """;

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError());

        verify(authService, never()).register(any(), any(), any(), any());
    }

    @Test
    @DisplayName("POST /api/auth/login com senha acima de 72 caracteres retorna 400")
    void loginWithOversizedPasswordReturns400() throws Exception {
        String body = "{\"username\": \"admin\", \"password\": \"" + "a".repeat(73) + "\"}";

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        verify(authService, never()).login(any(), any());
    }
}
