package com.ragbot.adapter.in.rest;

import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.in.ListDocumentsUseCase;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.domain.entity.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
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

@WebMvcTest(DocumentController.class)
@Import(TestWebSecurityConfig.class)
@DisplayName("DocumentController")
class DocumentControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean IngestDocumentUseCase ingestUseCase;
    @MockBean ListDocumentsUseCase  listUseCase;
    @MockBean AuditLogService       auditLogService;

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Document makeDoc(String id, String fileName, String status) {
        return new Document(id, fileName, "application/pdf", "storage/" + id,
                1024L, Instant.now(), "00000000-0000-0000-0000-000000000010",
                1, null, status);
    }

    // ── POST /api/documents ───────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("POST /api/documents retorna 202 Accepted para upload válido")
    void uploadShouldReturn202() throws Exception {
        when(ingestUseCase.ingest(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn(new IngestDocumentUseCase.IngestResult("doc-1", 1, "PROCESSING"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "manual.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 conteudo".getBytes());

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .param("namespaceId", "00000000-0000-0000-0000-000000000010")
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.processingStatus").value("PROCESSING"));
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("POST /api/documents retorna versão 2 quando documento já existe (supersede)")
    void uploadShouldReturnVersion2OnSupersede() throws Exception {
        when(ingestUseCase.ingest(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn(new IngestDocumentUseCase.IngestResult("doc-2", 2, "PROCESSING"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "manual.pdf", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.7 novo-conteudo".getBytes());

        mockMvc.perform(multipart("/api/documents")
                        .file(file)
                        .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.version").value(2));
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("upload com extensão fora da lista (.exe) retorna 422 e não ingere")
    void uploadRejectsDisallowedExtension() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "virus.exe", MediaType.APPLICATION_PDF_VALUE, "%PDF-1.4 fake".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(ingestUseCase);
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("upload .pdf cujo conteúdo não é PDF (tipo real diferente) retorna 422")
    void uploadRejectsFakePdf() throws Exception {
        byte[] exeHeader = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile(
                "file", "manual.pdf", MediaType.APPLICATION_PDF_VALUE, exeHeader);

        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(ingestUseCase);
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("upload .csv binário retorna 422")
    void uploadRejectsBinaryCsv() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "dados.csv", "text/csv", new byte[]{'a', 0, 'b', 0});

        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isUnprocessableEntity());
        verifyNoInteractions(ingestUseCase);
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("nome com caminho e markup é sanitizado; MIME vem da extensão, não do cliente")
    void uploadSanitizesNameAndIgnoresClientMime() throws Exception {
        when(ingestUseCase.ingest(anyString(), anyString(), any(), anyLong(), anyString()))
                .thenReturn(new IngestDocumentUseCase.IngestResult("doc-9", 1, "PROCESSING"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../etc/<img src=x onerror=alert(1)>.csv", "application/x-msdownload",
                "nome;idade\nana;30\n".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isAccepted());

        verify(ingestUseCase).ingest(eq("img src=x onerror=alert(1).csv"), eq("text/csv"), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("POST /api/documents sem autenticação retorna 401")
    void uploadUnauthenticatedReturns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", MediaType.APPLICATION_PDF_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/documents ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/documents retorna lista de documentos do namespace")
    void listByNamespaceShouldReturnDocuments() throws Exception {
        when(listUseCase.listByNamespace("00000000-0000-0000-0000-000000000010"))
                .thenReturn(List.of(makeDoc("d-1", "proc.pdf", "READY")));

        mockMvc.perform(get("/api/documents")
                        .param("namespaceId", "00000000-0000-0000-0000-000000000010"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("d-1"))
                .andExpect(jsonPath("$[0].fileName").value("proc.pdf"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/documents sem namespaceId retorna todos os documentos")
    void listAllShouldReturnAllDocuments() throws Exception {
        when(listUseCase.listAll()).thenReturn(List.of(
                makeDoc("d-1", "a.pdf", "READY"),
                makeDoc("d-2", "b.pdf", "READY")));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("GET /api/documents sem autenticação retorna 401")
    void listUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isUnauthorized());
    }

    // ── GET /api/documents/{id}/status ───────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/documents/{id}/status retorna 200 com status READY")
    void statusShouldReturn200WithStatus() throws Exception {
        when(listUseCase.findById("d-1")).thenReturn(Optional.of(makeDoc("d-1", "a.pdf", "READY")));

        mockMvc.perform(get("/api/documents/d-1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("d-1"))
                .andExpect(jsonPath("$.processingStatus").value("READY"));
    }

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/documents/{id}/status retorna 404 quando não encontrado")
    void statusShouldReturn404WhenNotFound() throws Exception {
        when(listUseCase.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/documents/nope/status"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/documents/{id}/versions ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "VIEWER")
    @DisplayName("GET /api/documents/{id}/versions retorna histórico de versões")
    void versionHistoryShouldReturnList() throws Exception {
        when(listUseCase.listVersionHistory("d-1"))
                .thenReturn(List.of(makeDoc("d-1", "a.pdf", "READY")));

        mockMvc.perform(get("/api/documents/d-1/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    // ── DELETE /api/documents/{id} ────────────────────────────────────────────

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("DELETE /api/documents/{id} retorna 204 e registra auditoria")
    void deleteShouldReturn204AndAudit() throws Exception {
        doNothing().when(listUseCase).delete("d-1");

        mockMvc.perform(delete("/api/documents/d-1").with(csrf()))
                .andExpect(status().isNoContent());

        verify(listUseCase).delete("d-1");
        verify(auditLogService).record(eq("admin"), any(), eq("d-1"), anyString(), any(), any());
    }

    @Test
    @DisplayName("DELETE /api/documents/{id} sem autenticação retorna 401")
    void deleteUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(delete("/api/documents/d-1").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    // ── POST /api/documents/{id}/retry ───────────────────────────────────────

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("POST /api/documents/{id}/retry retorna 202 quando FAILED")
    void retryShouldReturn202WhenStarted() throws Exception {
        when(listUseCase.retryProcessing("d-1")).thenReturn(true);
        when(listUseCase.findById("d-1")).thenReturn(Optional.of(makeDoc("d-1", "a.pdf", "PROCESSING")));

        mockMvc.perform(post("/api/documents/d-1/retry").with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.processingStatus").value("PROCESSING"));
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("POST /api/documents/{id}/retry retorna 409 quando não está em FAILED")
    void retryShouldReturn409WhenNotFailed() throws Exception {
        when(listUseCase.retryProcessing("d-1")).thenReturn(false);
        when(listUseCase.findById("d-1")).thenReturn(Optional.of(makeDoc("d-1", "a.pdf", "READY")));

        mockMvc.perform(post("/api/documents/d-1/retry").with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "editor", roles = "EDITOR")
    @DisplayName("POST /api/documents/{id}/retry retorna 404 quando documento não existe")
    void retryShouldReturn404WhenNotFound() throws Exception {
        when(listUseCase.retryProcessing("ghost")).thenReturn(false);
        when(listUseCase.findById("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/documents/ghost/retry").with(csrf()))
                .andExpect(status().isNotFound());
    }
}
