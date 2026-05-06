package com.ragbot.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.port.in.ManageNamespacesUseCase;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.domain.entity.Namespace;
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

@WebMvcTest(NamespaceController.class)
@Import(TestWebSecurityConfig.class)
@DisplayName("NamespaceController")
class NamespaceControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ManageNamespacesUseCase manageNamespacesUseCase;
    @MockBean AuditLogService         auditLogService;

    private Namespace ns(String id, String slug, String displayName) {
        return new Namespace(id, slug, displayName, Instant.now());
    }

    // ── GET /api/namespaces ───────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/namespaces retorna lista de namespaces para usuário autenticado")
    void listAllShouldReturnNamespaces() throws Exception {
        when(manageNamespacesUseCase.listAll()).thenReturn(List.of(
                ns(Namespace.DEFAULT_ID, "default", "Padrão"),
                ns("ns-2", "rh", "Recursos Humanos")));

        mockMvc.perform(get("/api/namespaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].slug").value("default"))
                .andExpect(jsonPath("$[1].slug").value("rh"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/namespaces retorna lista vazia quando não há namespaces")
    void listAllEmptyShouldReturnEmptyList() throws Exception {
        when(manageNamespacesUseCase.listAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/namespaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("GET /api/namespaces sem autenticação retorna 401")
    void listAllUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/namespaces"))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/namespaces ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/namespaces retorna 201 com namespace criado")
    void createShouldReturn201() throws Exception {
        when(manageNamespacesUseCase.create("financeiro", "Financeiro"))
                .thenReturn(ns("ns-fin", "financeiro", "Financeiro"));

        mockMvc.perform(post("/api/namespaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"slug":"financeiro","displayName":"Financeiro"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("ns-fin"))
                .andExpect(jsonPath("$.slug").value("financeiro"))
                .andExpect(jsonPath("$.displayName").value("Financeiro"));

        verify(auditLogService).record(eq("admin"), any(), eq("ns-fin"), eq("NAMESPACE"), anyString(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/namespaces com slug inválido retorna 400")
    void createWithInvalidSlugReturns400() throws Exception {
        mockMvc.perform(post("/api/namespaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"slug":"Slug Inválido!","displayName":"Teste"}
                        """))
                .andExpect(status().isBadRequest());

        verify(manageNamespacesUseCase, never()).create(anyString(), anyString());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/namespaces com slug em branco retorna 400")
    void createWithBlankSlugReturns400() throws Exception {
        mockMvc.perform(post("/api/namespaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"slug":"","displayName":"Teste"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("POST /api/namespaces com displayName em branco retorna 400")
    void createWithBlankDisplayNameReturns400() throws Exception {
        mockMvc.perform(post("/api/namespaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"slug":"valido","displayName":""}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("POST /api/namespaces com VIEWER retorna 403")
    void createAsViewerReturns403() throws Exception {
        mockMvc.perform(post("/api/namespaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"slug":"rh","displayName":"RH"}
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/namespaces sem autenticação retorna 401")
    void createUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/namespaces")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"slug":"rh","displayName":"RH"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    // ── DELETE /api/namespaces/{id} ───────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("DELETE /api/namespaces/{id} retorna 204 e registra auditoria")
    void deleteShouldReturn204AndAudit() throws Exception {
        doNothing().when(manageNamespacesUseCase).delete("ns-fin");

        mockMvc.perform(delete("/api/namespaces/ns-fin").with(csrf()))
                .andExpect(status().isNoContent());

        verify(manageNamespacesUseCase).delete("ns-fin");
        verify(auditLogService).record(eq("admin"), any(), eq("ns-fin"), eq("NAMESPACE"), isNull(), any());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("DELETE /api/namespaces/{id} com EDITOR retorna 403")
    void deleteAsEditorReturns403() throws Exception {
        mockMvc.perform(delete("/api/namespaces/ns-fin").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("DELETE /api/namespaces/{id} sem autenticação retorna 401")
    void deleteUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(delete("/api/namespaces/ns-fin").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
