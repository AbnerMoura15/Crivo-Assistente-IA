package com.ragbot.adapter.in.rest;

import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.RagObservabilityService;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.AuditEntry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminController.class)
@Import(TestWebSecurityConfig.class)
@DisplayName("AdminController")
class AdminControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AuditLogService        auditLogService;
    @MockBean RagObservabilityService observabilityService;

    // ── GET /api/admin/dashboard ──────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/dashboard retorna 200 com métricas")
    void dashboardShouldReturn200() throws Exception {
        when(observabilityService.getMetrics()).thenReturn(Map.of(
                "totalInteractions", 42,
                "answered", 38,
                "notFound", 4,
                "answeredRate", 90,
                "positiveFeedback", 20,
                "negativeFeedback", 2,
                "recentInteractions", List.of()
        ));

        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInteractions").value(42))
                .andExpect(jsonPath("$.answered").value(38))
                .andExpect(jsonPath("$.answeredRate").value(90));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/admin/dashboard com VIEWER retorna 403")
    void dashboardAsViewerReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/dashboard sem autenticação retorna 401")
    void dashboardUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/admin/audit-log ──────────────────────────────────────────────

    private AuditEntry makeEntry(String actor, AuditAction action) {
        return new AuditEntry("id-1", actor, action, null, "USER", null, "127.0.0.1", Instant.now());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/audit-log sem filtros retorna entradas recentes")
    void auditLogNoFilterReturnsRecent() throws Exception {
        when(auditLogService.findRecent(100)).thenReturn(List.of(
                makeEntry("alice", AuditAction.LOGIN_SUCCESS),
                makeEntry("bob",   AuditAction.DOCUMENT_UPLOAD)));

        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].actor").value("alice"))
                .andExpect(jsonPath("$[0].action").value("LOGIN_SUCCESS"))
                .andExpect(jsonPath("$[1].actor").value("bob"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/audit-log com filtro actor filtra por usuário")
    void auditLogWithActorFilterCallsFindByActor() throws Exception {
        when(auditLogService.findByActor("alice", 50)).thenReturn(List.of(
                makeEntry("alice", AuditAction.LOGIN_SUCCESS)));

        mockMvc.perform(get("/api/admin/audit-log")
                        .param("actor", "alice")
                        .param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].actor").value("alice"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/audit-log com filtro action filtra por ação")
    void auditLogWithActionFilterCallsFindByAction() throws Exception {
        when(auditLogService.findByAction(AuditAction.DOCUMENT_UPLOAD, 100)).thenReturn(List.of(
                makeEntry("editor", AuditAction.DOCUMENT_UPLOAD)));

        mockMvc.perform(get("/api/admin/audit-log").param("action", "DOCUMENT_UPLOAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].action").value("DOCUMENT_UPLOAD"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("GET /api/admin/audit-log sem entradas retorna lista vazia")
    void auditLogEmptyReturnsEmptyList() throws Exception {
        when(auditLogService.findRecent(anyInt())).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("GET /api/admin/audit-log com EDITOR retorna 403")
    void auditLogAsEditorReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/audit-log sem autenticação retorna 401")
    void auditLogUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/audit-log"))
                .andExpect(status().isUnauthorized());
    }
}
