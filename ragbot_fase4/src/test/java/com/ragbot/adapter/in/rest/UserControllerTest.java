package com.ragbot.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.port.in.ManageUsersUseCase;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.TokenUsageService;
import com.ragbot.domain.entity.User;
import com.ragbot.domain.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserController.class)
@Import(TestWebSecurityConfig.class)
@DisplayName("UserController")
class UserControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ManageUsersUseCase manageUsersUseCase;
    @MockBean AuditLogService    auditLogService;
    @MockBean TokenUsageService  tokenUsageService;

    private User makeUser(String id, String username, UserRole role, boolean active) {
        return new User(id, username, username + "@corp.com", "hash", role, active,
                Instant.now(), null);
    }

    // ── GET /api/users/me/usage ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("GET /api/users/me/usage retorna 200 com uso de tokens do usuário")
    void myUsageShouldReturn200() throws Exception {
        when(tokenUsageService.getCurrentMonthUsage("alice")).thenReturn(5_000L);
        when(tokenUsageService.getMonthlyLimit()).thenReturn(100_000L);

        mockMvc.perform(get("/api/users/me/usage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.tokensUsed").value(5000))
                .andExpect(jsonPath("$.monthlyLimit").value(100000))
                .andExpect(jsonPath("$.percentUsed").value(5.0));
    }

    @Test
    @DisplayName("GET /api/users/me/usage sem autenticação retorna 401")
    void myUsageUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me/usage"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/users ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("GET /api/users retorna lista de todos os usuários para ADMIN")
    void listAllShouldReturn200ForAdmin() throws Exception {
        when(manageUsersUseCase.listAll()).thenReturn(List.of(
                makeUser("u-1", "alice", UserRole.VIEWER, true),
                makeUser("u-2", "bob",   UserRole.EDITOR, true)));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("alice"))
                .andExpect(jsonPath("$[1].username").value("bob"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/users com VIEWER retorna 403")
    void listAllAsViewerReturns403() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/users sem autenticação retorna 401")
    void listAllUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/users ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/users cria usuário e retorna 201")
    void createUserShouldReturn201() throws Exception {
        User created = makeUser("u-3", "carlos", UserRole.EDITOR, true);
        when(manageUsersUseCase.createUser("carlos", "carlos@corp.com", "senha123", UserRole.EDITOR))
                .thenReturn(created);

        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "carlos",
                              "email": "carlos@corp.com",
                              "password": "senha123",
                              "role": "EDITOR"
                            }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("carlos"))
                .andExpect(jsonPath("$.role").value("EDITOR"));

        verify(auditLogService).record(eq("admin"), any(), eq("u-3"), eq("USER"), anyString(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/users com senha curta retorna 400")
    void createUserWithShortPasswordReturns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "diego",
                              "email": "diego@corp.com",
                              "password": "123",
                              "role": "VIEWER"
                            }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/users com email inválido retorna 400")
    void createUserWithInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "username": "eva",
                              "email": "email-invalido",
                              "password": "senha123",
                              "role": "VIEWER"
                            }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("POST /api/users com EDITOR retorna 403")
    void createUserAsEditorReturns403() throws Exception {
        mockMvc.perform(post("/api/users")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"username":"x","email":"x@x.com","password":"senha123","role":"VIEWER"}
                        """))
                .andExpect(status().isForbidden());
    }

    // ── PUT /api/users/{id}/role ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PUT /api/users/{id}/role atualiza perfil e retorna 200")
    void changeRoleShouldReturn200() throws Exception {
        User updated = makeUser("u-1", "alice", UserRole.EDITOR, true);
        when(manageUsersUseCase.changeRole("u-1", UserRole.EDITOR)).thenReturn(updated);

        mockMvc.perform(put("/api/users/u-1/role")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"role":"EDITOR"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("EDITOR"));
    }

    // ── PUT /api/users/{id}/deactivate ────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PUT /api/users/{id}/deactivate retorna 204")
    void deactivateShouldReturn204() throws Exception {
        doNothing().when(manageUsersUseCase).deactivate("u-1");

        mockMvc.perform(put("/api/users/u-1/deactivate").with(csrf()))
                .andExpect(status().isNoContent());

        verify(auditLogService).record(eq("admin"), any(), eq("u-1"), eq("USER"), isNull(), any());
    }

    // ── PUT /api/users/{id}/activate ─────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PUT /api/users/{id}/activate retorna 204")
    void activateShouldReturn204() throws Exception {
        doNothing().when(manageUsersUseCase).activate("u-1");

        mockMvc.perform(put("/api/users/u-1/activate").with(csrf()))
                .andExpect(status().isNoContent());

        verify(auditLogService).record(eq("admin"), any(), eq("u-1"), eq("USER"), isNull(), any());
    }
}
