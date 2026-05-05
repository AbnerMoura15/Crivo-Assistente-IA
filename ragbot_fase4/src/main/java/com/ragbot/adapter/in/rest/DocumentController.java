package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.in.ListDocumentsUseCase;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.Document;
import com.ragbot.domain.entity.Namespace;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);

    private final IngestDocumentUseCase ingestUseCase;
    private final ListDocumentsUseCase listUseCase;
    private final AuditLogService auditLogService;

    public DocumentController(IngestDocumentUseCase ingestUseCase,
                              ListDocumentsUseCase listUseCase,
                              AuditLogService auditLogService) {
        this.ingestUseCase = ingestUseCase;
        this.listUseCase = listUseCase;
        this.auditLogService = auditLogService;
    }

    /**
     * Upload de documento para um namespace (#33).
     *
     * Retorna HTTP 202 Accepted imediatamente (#22 — ingestão assíncrona).
     * O pipeline de chunking/embedding roda em background.
     * Use GET /{id}/status para acompanhar o progresso.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestDtos.IngestResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "namespaceId", defaultValue = Namespace.DEFAULT_ID) String namespaceId,
            Authentication auth,
            HttpServletRequest request) throws IOException {

        log.info("Upload recebido: {} ({} bytes) namespace={}",
                file.getOriginalFilename(), file.getSize(), namespaceId);

        IngestDocumentUseCase.IngestResult result = ingestUseCase.ingest(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize(),
                namespaceId
        );

        String actor = auth != null ? auth.getName() : "anônimo";
        auditLogService.record(actor, AuditAction.DOCUMENT_UPLOAD, result.documentId(), "DOCUMENT",
                "arquivo=" + file.getOriginalFilename() + " v" + result.version() + " ns=" + namespaceId,
                request.getRemoteAddr());

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(new RestDtos.IngestResponse(
                        result.documentId(),
                        "Arquivo '" + file.getOriginalFilename() + "' recebido (v" + result.version()
                        + "). Processando em background…",
                        result.version(),
                        result.processingStatus()
                ));
    }

    /**
     * Consulta o status de processamento assíncrono de um documento (#22).
     *
     * Retorna 200 com o status atual (PROCESSING | READY | FAILED).
     * Retorna 404 se o documento não existir.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<RestDtos.DocumentStatusResponse> status(@PathVariable String id) {
        return listUseCase.findById(id)
                .map(doc -> ResponseEntity.ok(RestDtos.DocumentStatusResponse.from(doc)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Lista documentos ACTIVE.
     *
     * @param namespaceId filtrar por namespace; se omitido, retorna todos os namespaces
     */
    @GetMapping
    public ResponseEntity<List<RestDtos.DocumentResponse>> list(
            @RequestParam(required = false) String namespaceId) {
        List<Document> docs = namespaceId != null
                ? listUseCase.listByNamespace(namespaceId)
                : listUseCase.listAll();
        return ResponseEntity.ok(docs.stream().map(RestDtos.DocumentResponse::from).toList());
    }

    /**
     * Histórico de versões de um documento (#26).
     * Retorna todas as versões (ACTIVE + SUPERSEDED), mais nova primeiro.
     */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<RestDtos.DocumentResponse>> versionHistory(@PathVariable String id) {
        return ResponseEntity.ok(
                listUseCase.listVersionHistory(id)
                        .stream().map(RestDtos.DocumentResponse::from).toList()
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id, Authentication auth,
                                       HttpServletRequest request) {
        listUseCase.delete(id);
        String actor = auth != null ? auth.getName() : "anônimo";
        auditLogService.record(actor, AuditAction.DOCUMENT_DELETE, id, "DOCUMENT", null,
                request.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }
}
