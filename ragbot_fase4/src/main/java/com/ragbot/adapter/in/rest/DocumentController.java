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
     * Se já existir um ACTIVE com o mesmo nome no namespace, cria nova versão (#26).
     *
     * @param namespaceId namespace de destino (default se omitido)
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestDtos.IngestResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "namespaceId", defaultValue = Namespace.DEFAULT_ID) String namespaceId,
            Authentication auth,
            HttpServletRequest request) throws IOException {

        log.info("Upload recebido: {} ({} bytes) namespace={}",
                file.getOriginalFilename(), file.getSize(), namespaceId);

        String docId = ingestUseCase.ingest(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize(),
                namespaceId
        );

        // Busca versão para incluir na resposta
        Document created = listUseCase.listAll().stream()
                .filter(d -> d.id().equals(docId))
                .findFirst()
                .orElse(null);
        int version = created != null ? created.version() : 1;

        String actor = auth != null ? auth.getName() : "anônimo";
        auditLogService.record(actor, AuditAction.DOCUMENT_UPLOAD, docId, "DOCUMENT",
                "arquivo=" + file.getOriginalFilename() + " v" + version + " ns=" + namespaceId,
                request.getRemoteAddr());

        return ResponseEntity.ok(new RestDtos.IngestResponse(docId,
                "Documento '" + file.getOriginalFilename() + "' indexado com sucesso (v" + version + ").",
                version));
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
