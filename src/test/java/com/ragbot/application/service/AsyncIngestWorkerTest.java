package com.ragbot.application.service;

import com.ragbot.application.port.out.DocumentParserPort;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.EmbeddingPort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do AsyncIngestWorker (#22).
 *
 * O worker é chamado diretamente (sem proxy @Async) — em testes unitários
 * isso é suficiente para cobrir as branches de sucesso e falha.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AsyncIngestWorker")
class AsyncIngestWorkerTest {

    @Mock DocumentParserPort parser;
    @Mock EmbeddingPort embeddingPort;
    @Mock VectorStorePort vectorStorePort;
    @Mock DocumentStoragePort storagePort;
    @Mock ChunkingStrategy chunkingStrategy;

    AsyncIngestWorker worker;

    private static final String DOC_ID     = "doc-42";
    private static final String FILE_NAME  = "manual.pdf";
    private static final String MIME_TYPE  = "application/pdf";
    private static final String NS_ID      = "ns-default";
    private static final int    VERSION    = 1;

    @BeforeEach
    void setUp() {
        when(parser.supports(MIME_TYPE)).thenReturn(true);
        worker = new AsyncIngestWorker(
                List.of(parser), embeddingPort, vectorStorePort, storagePort, chunkingStrategy);
    }

    // ── caminho feliz ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("processAsync PRONTO — chunks salvos e status atualizado para READY")
    void processAsync_success_savesChunksAndSetsReady() throws Exception {
        InputStream stream = new ByteArrayInputStream("conteúdo do pdf".getBytes());
        when(storagePort.retrieve(DOC_ID)).thenReturn(stream);
        when(parser.extractText(any(), eq(FILE_NAME))).thenReturn("Texto do documento.");
        when(chunkingStrategy.split("Texto do documento.")).thenReturn(List.of("Chunk A", "Chunk B"));
        when(embeddingPort.embed("Chunk A")).thenReturn(List.of(0.1f, 0.2f));
        when(embeddingPort.embed("Chunk B")).thenReturn(List.of(0.3f, 0.4f));

        worker.processAsync(DOC_ID, FILE_NAME, MIME_TYPE, NS_ID, VERSION);

        // dois chunks devem ter sido salvos
        ArgumentCaptor<Chunk> chunkCaptor = ArgumentCaptor.forClass(Chunk.class);
        verify(vectorStorePort, times(2)).save(chunkCaptor.capture());
        List<Chunk> saved = chunkCaptor.getAllValues();
        assertThat(saved.get(0).content()).isEqualTo("Chunk A");
        assertThat(saved.get(1).content()).isEqualTo("Chunk B");
        assertThat(saved.get(0).documentId()).isEqualTo(DOC_ID);

        // status final deve ser READY
        verify(storagePort).updateProcessingStatus(DOC_ID, "READY");
        verify(storagePort, never()).updateProcessingStatus(DOC_ID, "FAILED");
    }

    @Test
    @DisplayName("processAsync com metadados corretos no chunk")
    void processAsync_success_chunkHasCorrectMetadata() throws Exception {
        when(storagePort.retrieve(DOC_ID)).thenReturn(new ByteArrayInputStream("text".getBytes()));
        when(parser.extractText(any(), eq(FILE_NAME))).thenReturn("Único chunk");
        when(chunkingStrategy.split("Único chunk")).thenReturn(List.of("Único chunk"));
        when(embeddingPort.embed("Único chunk")).thenReturn(List.of(0.5f));

        worker.processAsync(DOC_ID, FILE_NAME, MIME_TYPE, NS_ID, VERSION);

        ArgumentCaptor<Chunk> captor = ArgumentCaptor.forClass(Chunk.class);
        verify(vectorStorePort).save(captor.capture());
        Chunk chunk = captor.getValue();
        assertThat(chunk.metadata()).containsEntry("source", FILE_NAME);
        assertThat(chunk.metadata()).containsEntry("namespace", NS_ID);
        assertThat(chunk.metadata()).containsEntry("version", "1");
        assertThat(chunk.metadata()).containsEntry("chunkIndex", "0");
        assertThat(chunk.metadata()).containsEntry("totalChunks", "1");
    }

    // ── caminho de falha ──────────────────────────────────────────────────────

    @Test
    @DisplayName("processAsync FALHA ao recuperar arquivo — status atualizado para FAILED")
    void processAsync_storageRetrieveThrows_setsFailed() {
        when(storagePort.retrieve(DOC_ID)).thenThrow(new RuntimeException("Disco cheio"));

        worker.processAsync(DOC_ID, FILE_NAME, MIME_TYPE, NS_ID, VERSION);

        verify(storagePort).updateProcessingStatus(DOC_ID, "FAILED");
        verify(vectorStorePort, never()).save(any());
    }

    @Test
    @DisplayName("processAsync FALHA na geração de embedding — status atualizado para FAILED")
    void processAsync_embeddingThrows_setsFailed() throws Exception {
        when(storagePort.retrieve(DOC_ID)).thenReturn(new ByteArrayInputStream("texto".getBytes()));
        when(parser.extractText(any(), eq(FILE_NAME))).thenReturn("Texto curto");
        when(chunkingStrategy.split("Texto curto")).thenReturn(List.of("Texto curto"));
        when(embeddingPort.embed(anyString())).thenThrow(new RuntimeException("API indisponível"));

        worker.processAsync(DOC_ID, FILE_NAME, MIME_TYPE, NS_ID, VERSION);

        verify(storagePort).updateProcessingStatus(DOC_ID, "FAILED");
        verify(vectorStorePort, never()).save(any());
    }

    @Test
    @DisplayName("processAsync sem parser compatível — status atualizado para FAILED")
    void processAsync_noParserSupports_setsFailed() {
        when(parser.supports(MIME_TYPE)).thenReturn(false);
        when(storagePort.retrieve(DOC_ID)).thenReturn(new ByteArrayInputStream("bytes".getBytes()));

        worker.processAsync(DOC_ID, FILE_NAME, MIME_TYPE, NS_ID, VERSION);

        verify(storagePort).updateProcessingStatus(DOC_ID, "FAILED");
        verify(vectorStorePort, never()).save(any());
    }
}
