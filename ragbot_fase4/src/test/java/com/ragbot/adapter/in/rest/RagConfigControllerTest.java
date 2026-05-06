package com.ragbot.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.RagConfigService;
import com.ragbot.domain.entity.RagConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RagConfigController.class)
@Import(TestWebSecurityConfig.class)
@DisplayName("RagConfigController")
class RagConfigControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RagConfigService ragConfigService;
    @MockBean AuditLogService  auditLogService;

    private static final RagConfig DEFAULT_CONFIG =
            new RagConfig(5, 0.10, 80, true, 0.85, 100_000L, 6);

    private static final String VALID_REQUEST = """
            {
              "topK": 5,
              "minScore": 0.10,
              "shortChunkLength": 80,
              "hybridEnabled": true,
              "semanticWeight": 0.85,
              "monthlyTokenLimit": 100000,
              "maxHistoryMessages": 6
            }
            """;

    // ── GET /api/admin/config ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("GET /api/admin/config retorna 200 com configuração atual")
    void getConfigShouldReturn200() throws Exception {
        when(ragConfigService.getConfig()).thenReturn(DEFAULT_CONFIG);

        mockMvc.perform(get("/api/admin/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topK").value(5))
                .andExpect(jsonPath("$.minScore").value(0.10))
                .andExpect(jsonPath("$.hybridEnabled").value(true))
                .andExpect(jsonPath("$.semanticWeight").value(0.85))
                .andExpect(jsonPath("$.monthlyTokenLimit").value(100000))
                .andExpect(jsonPath("$.maxHistoryMessages").value(6));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/admin/config com VIEWER retorna 403")
    void getConfigAsViewerReturns403() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/admin/config sem autenticação retorna 401")
    void getConfigUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/config"))
                .andExpect(status().isUnauthorized());
    }

    // ── PUT /api/admin/config ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PUT /api/admin/config retorna 200 e persiste nova configuração")
    void updateConfigShouldReturn200AndSave() throws Exception {
        when(ragConfigService.getConfig()).thenReturn(DEFAULT_CONFIG);
        doNothing().when(ragConfigService).updateConfig(any());

        mockMvc.perform(put("/api/admin/config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topK").value(5))
                .andExpect(jsonPath("$.hybridEnabled").value(true));

        verify(ragConfigService).updateConfig(any(RagConfig.class));
        verify(auditLogService).record(eq("admin"), any(), isNull(), eq("RAG_CONFIG"), anyString(), any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PUT /api/admin/config com topK inválido retorna 400")
    void updateConfigWithInvalidTopKReturns400() throws Exception {
        mockMvc.perform(put("/api/admin/config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "topK": 0,
                              "minScore": 0.10,
                              "shortChunkLength": 80,
                              "hybridEnabled": true,
                              "semanticWeight": 0.85,
                              "monthlyTokenLimit": 100000,
                              "maxHistoryMessages": 6
                            }
                        """))
                .andExpect(status().isBadRequest());

        verify(ragConfigService, never()).updateConfig(any());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PUT /api/admin/config com minScore > 1.0 retorna 400")
    void updateConfigWithMinScoreAbove1Returns400() throws Exception {
        mockMvc.perform(put("/api/admin/config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "topK": 5,
                              "minScore": 1.5,
                              "shortChunkLength": 80,
                              "hybridEnabled": true,
                              "semanticWeight": 0.85,
                              "monthlyTokenLimit": 100000,
                              "maxHistoryMessages": 6
                            }
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "EDITOR")
    @DisplayName("PUT /api/admin/config com EDITOR retorna 403")
    void updateConfigAsEditorReturns403() throws Exception {
        mockMvc.perform(put("/api/admin/config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("PUT /api/admin/config com monthlyTokenLimit negativo retorna 400")
    void updateConfigWithNegativeTokenLimitReturns400() throws Exception {
        mockMvc.perform(put("/api/admin/config")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "topK": 5,
                              "minScore": 0.10,
                              "shortChunkLength": 80,
                              "hybridEnabled": false,
                              "semanticWeight": 0.50,
                              "monthlyTokenLimit": -1,
                              "maxHistoryMessages": 6
                            }
                        """))
                .andExpect(status().isBadRequest());
    }
}
