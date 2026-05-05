package com.ragbot.application.service;

import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.port.out.ChatHistoryPort;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.EmbeddingPort;
import com.ragbot.application.port.out.LlmPort;
import com.ragbot.application.port.out.RagConfigPort;
import com.ragbot.application.port.out.TokenUsagePort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.ChatSession;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;
import com.ragbot.domain.entity.RagConfig;
import com.ragbot.domain.entity.ScoredChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("QueryChatService")
class QueryChatServiceTest {

    @Mock EmbeddingPort embeddingPort;
    @Mock VectorStorePort vectorStorePort;
    @Mock LlmPort llmPort;
    @Mock DocumentStoragePort documentStoragePort;
    @Mock RagObservabilityService observabilityService;
    @Mock ChatHistoryPort chatHistoryPort;
    @Mock TokenUsagePort tokenUsagePort;

    private final PiiMaskingService piiMaskingService = new PiiMaskingService();

    QueryChatService service;

    private static final ChatSession FAKE_SESSION = new ChatSession(
            "sess-1", "user", Instant.now(), Instant.now(), List.of());

    /** Documento simples no namespace padrão, v1. */
    private static Document doc(String id, String name) {
        return new Document(id, name, "application/pdf", "/path", 1024L, Instant.now(),
                Namespace.DEFAULT_ID, 1, null);
    }

    @BeforeEach
    void setUp() {
        ChatHistoryService chatHistoryService = new ChatHistoryService(chatHistoryPort);
        // monthly limit = 100_000; current usage = 0 → never throws
        TokenUsageService tokenUsageService = new TokenUsageService(tokenUsagePort, 100_000L);

        // #25 — RagConfigService com mock port (topK=2, minScore=0.0, hybrid=false)
        RagConfigPort configPort = mock(RagConfigPort.class);
        when(configPort.load()).thenReturn(new RagConfig(2, 0.0, 80, false, 0.7, 100_000L));
        RagConfigService ragConfigService = new RagConfigService(configPort);

        service = new QueryChatService(
                embeddingPort, vectorStorePort, llmPort,
                documentStoragePort, piiMaskingService,
                observabilityService, chatHistoryService,
                tokenUsageService, ragConfigService
        );
        // sessão padrão para todos os testes
        when(chatHistoryPort.createSession(anyString())).thenReturn(FAKE_SESSION);
        when(chatHistoryPort.findSession(anyString())).thenReturn(Optional.empty());
        when(chatHistoryPort.loadMessages(anyString())).thenReturn(List.of());
        // uso de tokens = 0 por padrão
        when(tokenUsagePort.getMonthlyUsage(anyString(), anyInt())).thenReturn(0L);
    }

    @Test
    @DisplayName("sem documentos deve retornar mensagem informativa e persistir no histórico")
    void shouldReturnMessageWhenNoDocumentsFound() {
        // #33: QueryChatService agora chama findByNamespace, não findAll
        when(documentStoragePort.findByNamespace(anyString())).thenReturn(List.of());
        when(observabilityService.recordInteraction(any(), any(), any(), any())).thenReturn("id-1");

        QueryChatUseCase.ChatResult result = service.queryWithSession("Pergunta X?", null, "user");

        assertThat(result.answer()).contains("Nenhum documento");
        assertThat(result.interactionId()).isEqualTo("id-1");
        assertThat(result.sessionId()).isEqualTo("sess-1");
        verify(chatHistoryPort).addMessage(eq("sess-1"), argThat(m -> "user".equals(m.role())));
        verify(chatHistoryPort).addMessage(eq("sess-1"), argThat(m -> "assistant".equals(m.role())));
    }

    @Test
    @DisplayName("com chunks relevantes deve chamar LLM e registrar interação")
    void shouldCallLlmAndRecordInteraction() {
        Chunk chunk = new Chunk("1", "doc1", "Conteúdo relevante.", List.of(), Map.of("source", "manual.pdf"));

        when(documentStoragePort.findByNamespace(anyString())).thenReturn(List.of(doc("doc1", "manual.pdf")));
        when(embeddingPort.embed(anyString())).thenReturn(List.of(0.1f, 0.2f));
        // #33: findSimilar agora recebe 3 args (list, topK, namespaceId)
        when(vectorStorePort.findSimilar(anyList(), anyInt(), anyString()))
                .thenReturn(List.of(new ScoredChunk(chunk, 0.9)));
        when(llmPort.complete(anyString(), anyList())).thenReturn("Resposta gerada");
        when(observabilityService.recordInteraction(any(), any(), any(), any())).thenReturn("id-abc");

        QueryChatUseCase.ChatResult result = service.queryWithSession("Pergunta X?", null, "joao");

        assertThat(result.answer()).isEqualTo("Resposta gerada");
        assertThat(result.interactionId()).isEqualTo("id-abc");
        assertThat(result.sessionId()).isEqualTo("sess-1");
        verify(observabilityService).recordInteraction(eq("joao"), eq("Pergunta X?"), eq("Resposta gerada"), anyList());
    }

    @Test
    @DisplayName("sessionId existente deve reusar sessão e carregar histórico")
    void shouldReuseExistingSession() {
        ChatSession existingSession = new ChatSession(
                "sess-existing", "joao", Instant.now(), Instant.now(), List.of());
        when(chatHistoryPort.findSession("sess-existing")).thenReturn(Optional.of(existingSession));
        when(chatHistoryPort.loadMessages("sess-existing")).thenReturn(List.of());
        when(documentStoragePort.findByNamespace(anyString())).thenReturn(List.of());
        when(observabilityService.recordInteraction(any(), any(), any(), any())).thenReturn("id-x");

        QueryChatUseCase.ChatResult result = service.queryWithSession("Oi?", "sess-existing", "joao");

        assertThat(result.sessionId()).isEqualTo("sess-existing");
        verify(chatHistoryPort, never()).createSession(anyString());
    }

    @Test
    @DisplayName("PII deve ser mascarada antes de enviar ao LLM")
    void shouldMaskPiiBeforeSendingToLlm() {
        Chunk chunk = new Chunk("1", "doc1", "CPF 123.456.789-00 salário R$ 5.000,00",
                List.of(), Map.of("source", "rh.csv"));

        when(documentStoragePort.findByNamespace(anyString())).thenReturn(
                List.of(new Document("doc1", "rh.csv", "text/csv", "/path", 512L, Instant.now(),
                        Namespace.DEFAULT_ID, 1, null)));
        when(embeddingPort.embed(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorStorePort.findSimilar(anyList(), anyInt(), anyString()))
                .thenReturn(List.of(new ScoredChunk(chunk, 0.9)));
        when(llmPort.complete(anyString(), anyList())).thenReturn("resposta");
        when(observabilityService.recordInteraction(any(), any(), any(), any())).thenReturn("id-1");

        service.queryWithSession("Qual o salário?", null, "user");

        verify(llmPort).complete(argThat(prompt ->
                !prompt.contains("123.456.789-00") && prompt.contains("[CPF]")
        ), anyList());
    }
}
