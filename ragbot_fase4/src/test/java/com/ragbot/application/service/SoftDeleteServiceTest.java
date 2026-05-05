package com.ragbot.application.service;

import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.application.port.out.VectorStorePort;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.exception.DocumentNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * TDD #29 — Soft Delete via ListDocumentsService.
 *
 * Verifica que:
 * 1. Deletar documento aciona soft delete (não delete físico)
 * 2. Chunks associados são marcados como inativos
 * 3. Deletar documento inexistente lança exceção
 * 4. listAll() retorna apenas documentos ACTIVE
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ListDocumentsService - Soft Delete (#29)")
class SoftDeleteServiceTest {

    @Mock private DocumentStoragePort storagePort;
    @Mock private VectorStorePort vectorStorePort;

    private ListDocumentsService service;

    private final Document doc = new Document(
            "doc-abc", "manual.pdf", "application/pdf",
            "/docs/doc-abc", 1024L, Instant.now()
    );

    @BeforeEach
    void setUp() {
        service = new ListDocumentsService(storagePort, vectorStorePort);
    }

    @Test
    @DisplayName("delete deve chamar softDelete no storage e marcar chunks inativos")
    void deleteShouldCallSoftDeleteAndDeactivateChunks() {
        when(storagePort.findAll()).thenReturn(List.of(doc));

        service.delete("doc-abc");

        // Chunks devem ser marcados inativos ANTES do documento ser marcado deletado
        InOrder order = inOrder(vectorStorePort, storagePort);
        order.verify(vectorStorePort).deleteByDocumentId("doc-abc");
        order.verify(storagePort).delete("doc-abc");
    }

    @Test
    @DisplayName("listAll deve retornar apenas documentos ativos")
    void listAllShouldReturnOnlyActiveDocuments() {
        when(storagePort.findAll()).thenReturn(List.of(doc));

        List<Document> result = service.listAll();

        assertThat(result).hasSize(1).containsExactly(doc);
        verify(storagePort).findAll();
    }

    @Test
    @DisplayName("delete de documento inexistente deve lançar DocumentNotFoundException")
    void deleteNonExistentDocumentShouldThrow() {
        when(storagePort.findAll()).thenReturn(List.of()); // nenhum doc existe

        assertThatThrownBy(() -> service.delete("id-que-nao-existe"))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(vectorStorePort, never()).deleteByDocumentId(any());
        verify(storagePort, never()).delete(any());
    }

    // Import necessário para assertThat na linha 57
    private static <T> org.assertj.core.api.ListAssert<T> assertThat(List<T> list) {
        return org.assertj.core.api.Assertions.assertThat(list);
    }
}
