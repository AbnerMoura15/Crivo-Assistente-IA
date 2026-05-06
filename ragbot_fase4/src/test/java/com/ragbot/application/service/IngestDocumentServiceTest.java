package com.ragbot.application.service;

import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testes unitários do IngestDocumentService (#22 + #26).
 *
 * Verifica o fluxo de ingestão assíncrona:
 * - primeiro upload → store + dispara async
 * - re-upload mesmo nome → supersede versão anterior + nova versão
 * - IngestResult retorna imediatamente com status PROCESSING
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IngestDocumentService")
class IngestDocumentServiceTest {

    @Mock DocumentStoragePort storagePort;
    @Mock VectorStorePort vectorStorePort;
    @Mock AsyncIngestWorker asyncWorker;

    IngestDocumentService service;

    private static final String NS_ID     = Namespace.DEFAULT_ID;
    private static final String FILE_NAME = "relatorio.pdf";
    private static final String MIME      = "application/pdf";
    private static final long   SIZE      = 1024L;

    @BeforeEach
    void setUp() {
        service = new IngestDocumentService(storagePort, vectorStorePort, asyncWorker);
    }

    // ── primeiro upload ───────────────────────────────────────────────────────

    @Test
    @DisplayName("primeiro upload: grava com v1 e retorna PROCESSING imediatamente")
    void firstUpload_storesVersion1AndReturnsProcessing() {
        when(storagePort.findActiveByFileName(FILE_NAME, NS_ID)).thenReturn(Optional.empty());
        when(storagePort.store(eq(FILE_NAME), eq(MIME), eq(SIZE), any(), eq(NS_ID), eq(1), isNull()))
                .thenReturn("doc-001");

        InputStream stream = new ByteArrayInputStream("dados".getBytes());
        IngestDocumentUseCase.IngestResult result = service.ingest(FILE_NAME, MIME, stream, SIZE, NS_ID);

        assertThat(result.documentId()).isEqualTo("doc-001");
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.processingStatus()).isEqualTo("PROCESSING");

        verify(storagePort, never()).supersede(any());
        verify(vectorStorePort, never()).deleteByDocumentId(any());
        verify(asyncWorker).processAsync("doc-001", FILE_NAME, MIME, NS_ID, 1);
    }

    @Test
    @DisplayName("primeiro upload: disparo async ocorre após store")
    void firstUpload_asyncFiredAfterStore() {
        when(storagePort.findActiveByFileName(FILE_NAME, NS_ID)).thenReturn(Optional.empty());
        when(storagePort.store(any(), any(), anyLong(), any(), any(), anyInt(), any()))
                .thenReturn("doc-new");

        service.ingest(FILE_NAME, MIME, new ByteArrayInputStream(new byte[0]), SIZE, NS_ID);

        InOrder order = inOrder(storagePort, asyncWorker);
        order.verify(storagePort).store(any(), any(), anyLong(), any(), any(), anyInt(), any());
        order.verify(asyncWorker).processAsync(eq("doc-new"), any(), any(), any(), anyInt());
    }

    // ── re-upload / versionamento (#26) ───────────────────────────────────────

    @Test
    @DisplayName("re-upload mesmo nome: supersede anterior e cria v2")
    void reUpload_supersedesPreviousAndCreatesV2() {
        Document prev = new Document("doc-old", FILE_NAME, MIME,
                "/path/old", 512L, Instant.now(), NS_ID, 1, null);
        when(storagePort.findActiveByFileName(FILE_NAME, NS_ID)).thenReturn(Optional.of(prev));
        when(storagePort.store(eq(FILE_NAME), eq(MIME), eq(SIZE), any(), eq(NS_ID), eq(2), eq("doc-old")))
                .thenReturn("doc-new");

        IngestDocumentUseCase.IngestResult result =
                service.ingest(FILE_NAME, MIME, new ByteArrayInputStream(new byte[0]), SIZE, NS_ID);

        assertThat(result.version()).isEqualTo(2);
        assertThat(result.documentId()).isEqualTo("doc-new");

        verify(vectorStorePort).deleteByDocumentId("doc-old");
        verify(storagePort).supersede("doc-old");
    }

    @Test
    @DisplayName("re-upload: chunks da versão anterior são deletados antes do store")
    void reUpload_deletesOldChunksBeforeStore() {
        Document prev = new Document("doc-old", FILE_NAME, MIME,
                "/path/old", 512L, Instant.now(), NS_ID, 1, null);
        when(storagePort.findActiveByFileName(FILE_NAME, NS_ID)).thenReturn(Optional.of(prev));
        when(storagePort.store(any(), any(), anyLong(), any(), any(), anyInt(), any()))
                .thenReturn("doc-v2");

        service.ingest(FILE_NAME, MIME, new ByteArrayInputStream(new byte[0]), SIZE, NS_ID);

        InOrder order = inOrder(vectorStorePort, storagePort);
        order.verify(vectorStorePort).deleteByDocumentId("doc-old");
        order.verify(storagePort).supersede("doc-old");
        order.verify(storagePort).store(any(), any(), anyLong(), any(), any(), eq(2), any());
    }

    // ── default namespace ─────────────────────────────────────────────────────

    @Test
    @DisplayName("ingest sem namespaceId usa DEFAULT_ID via default method")
    void defaultNamespaceMethod_usesDefaultId() {
        when(storagePort.findActiveByFileName(FILE_NAME, NS_ID)).thenReturn(Optional.empty());
        when(storagePort.store(any(), any(), anyLong(), any(), eq(NS_ID), anyInt(), any()))
                .thenReturn("doc-def");

        IngestDocumentUseCase.IngestResult result =
                service.ingest(FILE_NAME, MIME, new ByteArrayInputStream(new byte[0]), SIZE);

        assertThat(result.documentId()).isEqualTo("doc-def");
        verify(storagePort).store(any(), any(), anyLong(), any(), eq(NS_ID), anyInt(), any());
    }
}
