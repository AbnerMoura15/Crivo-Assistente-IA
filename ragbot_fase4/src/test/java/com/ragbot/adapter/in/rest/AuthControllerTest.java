package com.ragbot.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.service.AuthService;
import com.ragbot.domain.exception.InvalidCredentialsException;
import com.ragbot.domain.exception.UserAlreadyExistsException;
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
    @DisplayName("POST /api/auth/register com dados válidos retorna 201")
    void registerWithValidDataReturns201() throws Exception {
        doNothing().when(authService).register(any(), any(), any(), any());

        String body = """
                {
                  "username": "joao",
                  "email": "joao@fab.com",
                  "password": "senha123",
                  "role": "VIEWER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("POST /api/auth/register com username duplicado retorna 409")
    void registerWithDuplicateUsernameReturns409() throws Exception {
        doThrow(new UserAlreadyExistsException("username", "joao"))
                .when(authService).register(any(), any(), any(), any());

        String body = """
                {
                  "username": "joao",
                  "email": "joao@fab.com",
                  "password": "senha123",
                  "role": "VIEWER"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }
}
