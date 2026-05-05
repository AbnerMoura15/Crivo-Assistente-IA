package com.ragbot.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.service.RagObservabilityService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TDD #9 — FeedbackController.
 * Testa o endpoint de feedback 👍/👎 por interação.
 */
@WebMvcTest(FeedbackController.class)
@Import(TestSecurityConfig.class)
@DisplayName("FeedbackController")
class FeedbackControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockBean  RagObservabilityService observabilityService;

    @Test
    @WithMockUser(username = "joao", roles = "VIEWER")
    @DisplayName("POST /api/feedback com POSITIVE deve retornar 204")
    void positiveFeedbackShouldReturn204() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"interactionId": "abc-123", "positive": true}
                        """))
                .andExpect(status().isNoContent());

        verify(observabilityService).recordFeedback("abc-123", true, "joao");
    }

    @Test
    @WithMockUser(username = "maria", roles = "VIEWER")
    @DisplayName("POST /api/feedback com NEGATIVE deve retornar 204")
    void negativeFeedbackShouldReturn204() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"interactionId": "def-456", "positive": false}
                        """))
                .andExpect(status().isNoContent());

        verify(observabilityService).recordFeedback("def-456", false, "maria");
    }

    @Test
    @DisplayName("POST /api/feedback sem autenticação deve retornar 401")
    void unauthenticatedFeedbackShouldReturn401() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"interactionId": "abc-123", "positive": true}
                        """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("POST /api/feedback sem interactionId deve retornar 400")
    void feedbackWithoutIdShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/feedback")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
