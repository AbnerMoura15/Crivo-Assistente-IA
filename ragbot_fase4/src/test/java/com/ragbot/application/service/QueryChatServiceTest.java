package com.ragbot.application.service;

import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.EmbeddingPort;
import com.ragbot.application.port.out.LlmPort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.entity.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QueryChatService")
class QueryChatServiceTest {

    @Mock EmbeddingPort embeddingPort;
    @Mock VectorStorePort vectorStorePort;
    @Mock LlmPort llmPort;
    @Mock DocumentStoragePort documentStoragePort;

    // PiiMaskingService é leve — usamos a instância real, sem mock
    private final PiiMaskingService piiMaskingService = new PiiMaskingService();

    QueryChatService service;

    @BeforeEach
    void setUp() {
        service = new QueryChatService(
                embeddingPort, vectorStorePort, llmPort,
                documentStoragePort, piiMaskingService, 2
        );
    }

    @Test
    @DisplayName("sem documentos deve retornar mensagem informativa")
    void shouldReturnMessageWhenNoDocumentsFound() {
        when(documentStoragePort.findAll()).thenReturn(List.of());

        String result = service.query("Qual o procedimento X?", List.of());

        assertThat(result).contains("Nenhum documento");
    }

    @Test
    @DisplayName("com chunks relevantes deve chamar LLM e retornar resposta")
    void shouldCallLlmWithContextWhenChunksFound() {
        when(documentStoragePort.findAll()).thenReturn(List.of(
                new Document("doc1", "manual.pdf", "application/pdf", "/path", 1024L, Instant.now())
        ));
        when(embeddingPort.embed(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorStorePort.findSimilar(anyList(), anyInt())).thenReturn(List.of(
                new Chunk("1", "doc1", "Conteúdo relevante.", List.of(), Map.of("source", "manual.pdf"))
        ));
        when(llmPort.complete(anyString(), anyList())).thenReturn("Resposta gerada pelo LLM");

        String result = service.query("Qual o procedimento X?", List.of());

        assertThat(result).isEqualTo("Resposta gerada pelo LLM");
    }

    @Test
    @DisplayName("PII no contexto deve ser mascarada antes de enviar ao LLM (#31)")
    void shouldMaskPiiInContextBeforeSendingToLlm() {
        when(documentStoragePort.findAll()).thenReturn(List.of(
                new Document("doc1", "rh.csv", "text/csv", "/path", 512L, Instant.now())
        ));
        when(embeddingPort.embed(anyString())).thenReturn(List.of(0.1f, 0.2f));
        when(vectorStorePort.findSimilar(anyList(), anyInt())).thenReturn(List.of(
                new Chunk("1", "doc1",
                        "Funcionário CPF 123.456.789-00 salário R$ 5.000,00",
                        List.of(), Map.of("source", "rh.csv"))
        ));
        when(llmPort.complete(anyString(), anyList())).thenReturn("resposta");

        service.query("Qual o salário?", List.of());

        // Verifica que o systemPrompt enviado ao LLM NÃO contém o CPF original
        verify(llmPort).complete(argThat(prompt ->
                !prompt.contains("123.456.789-00")
                && prompt.contains("[CPF]")
                && !prompt.contains("5.000,00")
                && prompt.contains("[VALOR]")
        ), anyList());
    }
}
