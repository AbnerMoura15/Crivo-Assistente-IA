package com.ragbot.adapter.in.rest;

import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.in.ListDocumentsUseCase;
import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.service.AuthService;
import com.ragbot.application.service.TokenUsageService;
import com.ragbot.application.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TDD — Controle de acesso por perfil.
 * Verifica que cada endpoint respeita as restrições de role.
 *
 * Regras de negócio:
 *   VIEWER  → apenas GET /api/chat POST /api/chat
 *   EDITOR  → VIEWER + POST /api/documents
 *   ADMIN   → EDITOR + DELETE /api/documents/{id} + /api/users/**
 *   Anônimo → apenas POST /api/auth/login
 */
@WebMvcTest({DocumentController.class, ChatController.class, UserController.class, AuthController.class})
@Import(TestWebSecurityConfig.class)
@DisplayName("Controle de Acesso por Perfil")
class SecurityEndpointsTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean private IngestDocumentUseCase ingestUseCase;
    @MockBean private ListDocumentsUseCase listUseCase;
    @MockBean private QueryChatUseCase queryUseCase;
    @MockBean private AuthService authService;
    @MockBean private UserService userService;
    @MockBean private TokenUsageService tokenUsageService;

    // ── Acesso anônimo ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/documents sem autenticação retorna 401")
    void anonymousCannotListDocuments() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/chat sem autenticação retorna 401")
    void anonymousCannotChat() throws Exception {
        mockMvc.perform(post("/api/chat"))
                .andExpect(status().isUnauthorized());
    }

    // ── VIEWER ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("VIEWER pode listar documentos")
    void viewerCanListDocuments() throws Exception {
        mockMvc.perform(get("/api/documents").with(user("user").roles("VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("VIEWER não pode fazer upload de documentos (403)")
    void viewerCannotUploadDocuments() throws Exception {
        mockMvc.perform(post("/api/documents").with(user("user").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER não pode deletar documentos (403)")
    void viewerCannotDeleteDocuments() throws Exception {
        mockMvc.perform(delete("/api/documents/some-id").with(user("user").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("VIEWER não pode acessar /api/users (403)")
    void viewerCannotAccessUsers() throws Exception {
        mockMvc.perform(get("/api/users").with(user("user").roles("VIEWER")))
                .andExpect(status().isForbidden());
    }

    // ── EDITOR ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("EDITOR pode fazer upload de documentos")
    void editorCanUploadDocuments() throws Exception {
        mockMvc.perform(post("/api/documents").with(user("editor").roles("EDITOR")))
                .andExpect(status().is4xxClientError()); // 400 por falta do arquivo — não 403
    }

    @Test
    @DisplayName("EDITOR não pode deletar documentos (403)")
    void editorCannotDeleteDocuments() throws Exception {
        mockMvc.perform(delete("/api/documents/some-id").with(user("editor").roles("EDITOR")))
                .andExpect(status().isForbidden());
    }

    // ── ADMIN ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("ADMIN pode deletar documentos")
    void adminCanDeleteDocuments() throws Exception {
        mockMvc.perform(delete("/api/documents/some-id").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ADMIN pode acessar /api/users")
    void adminCanAccessUsers() throws Exception {
        mockMvc.perform(get("/api/users").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk());
    }
}
