package com.ragbot.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.service.ChatHistoryService;
import com.ragbot.domain.entity.ChatSession;
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
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
@Import(TestWebSecurityConfig.class)
@DisplayName("ChatController")
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean QueryChatUseCase queryChatUseCase;
    @MockBean ChatHistoryService chatHistoryService;

    // ── POST /api/chat ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("POST /api/chat retorna 200 com resposta e sources")
    void chatShouldReturn200WithAnswerAndSources() throws Exception {
        when(queryChatUseCase.queryWithSession(anyString(), isNull(), eq("alice"), anyString()))
                .thenReturn(new QueryChatUseCase.ChatResult(
                        "A regra é X.", "inter-1", "sess-1", List.of("manual.pdf")));

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"question":"Qual a regra?","sessionId":null}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("A regra é X."))
                .andExpect(jsonPath("$.interactionId").value("inter-1"))
                .andExpect(jsonPath("$.sessionId").value("sess-1"))
                .andExpect(jsonPath("$.sources[0]").value("manual.pdf"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("POST /api/chat com namespaceId usa o namespace informado")
    void chatWithNamespacePropagatesIt() throws Exception {
        String nsId = "00000000-0000-0000-0000-000000000020";
        when(queryChatUseCase.queryWithSession(anyString(), isNull(), eq("alice"), eq(nsId)))
                .thenReturn(new QueryChatUseCase.ChatResult("Ok.", "i-2", "s-2", List.of()));

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"question":"teste","sessionId":null,"namespaceId":"%s"}
                        """.formatted(nsId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Ok."));

        verify(queryChatUseCase).queryWithSession(eq("teste"), isNull(), eq("alice"), eq(nsId));
    }

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("POST /api/chat sem namespaceId usa namespace padrão")
    void chatWithoutNamespaceUsesDefault() throws Exception {
        when(queryChatUseCase.queryWithSession(anyString(), isNull(), anyString(),
                eq("00000000-0000-0000-0000-000000000010")))
                .thenReturn(new QueryChatUseCase.ChatResult("Resposta.", "i-3", "s-3", List.of()));

        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"question":"pergunta"}
                        """))
                .andExpect(status().isOk());

        verify(queryChatUseCase).queryWithSession(anyString(), any(), anyString(),
                eq("00000000-0000-0000-0000-000000000010"));
    }

    @Test
    @DisplayName("POST /api/chat sem autenticação retorna 401")
    void chatUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/chat")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"question":"Qual a regra?"}
                        """))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/chat/sessions ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("GET /api/chat/sessions retorna lista de sessões do usuário")
    void listSessionsShouldReturnUserSessions() throws Exception {
        ChatSession session = new ChatSession("s-1", "alice", Instant.now(), Instant.now(), null);
        when(chatHistoryService.listUserSessions("alice")).thenReturn(List.of(session));

        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("s-1"))
                .andExpect(jsonPath("$[0].username").value("alice"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("GET /api/chat/sessions retorna lista vazia quando não há sessões")
    void listSessionsEmptyShouldReturnEmptyList() throws Exception {
        when(chatHistoryService.listUserSessions("alice")).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── GET /api/chat/sessions/{sessionId} ───────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("GET /api/chat/sessions/{id} retorna 200 para sessão do próprio usuário")
    void getSessionShouldReturn200ForOwner() throws Exception {
        ChatSession session = new ChatSession("sess-x", "alice", Instant.now(), Instant.now(), List.of());
        when(chatHistoryService.getSessionWithMessages("sess-x")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/chat/sessions/sess-x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("sess-x"))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("GET /api/chat/sessions/{id} retorna 404 para sessão de outro usuário")
    void getSessionShouldReturn404ForOtherUser() throws Exception {
        ChatSession session = new ChatSession("sess-y", "bob", Instant.now(), Instant.now(), List.of());
        when(chatHistoryService.getSessionWithMessages("sess-y")).thenReturn(Optional.of(session));

        mockMvc.perform(get("/api/chat/sessions/sess-y"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("GET /api/chat/sessions/{id} retorna 404 quando sessão não existe")
    void getSessionShouldReturn404WhenNotFound() throws Exception {
        when(chatHistoryService.getSessionWithMessages("nonexistent")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/chat/sessions/nonexistent"))
                .andExpect(status().isNotFound());
    }

    // ── DELETE /api/chat/sessions/{sessionId} ────────────────────────────────

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("DELETE /api/chat/sessions/{id} retorna 204 quando sessão removida")
    void deleteSessionShouldReturn204() throws Exception {
        when(chatHistoryService.deleteSession("sess-x", "alice")).thenReturn(true);

        mockMvc.perform(delete("/api/chat/sessions/sess-x").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "alice", roles = "VIEWER")
    @DisplayName("DELETE /api/chat/sessions/{id} retorna 404 quando não pertence ao usuário")
    void deleteSessionShouldReturn404WhenNotOwner() throws Exception {
        when(chatHistoryService.deleteSession("sess-bob", "alice")).thenReturn(false);

        mockMvc.perform(delete("/api/chat/sessions/sess-bob").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("DELETE /api/chat/sessions/{id} sem autenticação retorna 401")
    void deleteSessionUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(delete("/api/chat/sessions/any").with(csrf()))
                .andExpect(status().isUnauthorized());
    }
}
