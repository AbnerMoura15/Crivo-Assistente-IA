package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.in.ListDocumentsUseCase;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.domain.entity.AuditAction;
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

    public DocumentController(IngestDocumentUseCase ingestUseCase, ListDocumentsUseCase listUseCase,
                               AuditLogService auditLogService) {
        this.ingestUseCase = ingestUseCase;
        this.listUseCase = listUseCase;
        this.auditLogService = auditLogService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestDtos.IngestResponse> upload(
            @RequestParam("file") MultipartFile file,
            Authentication auth,
            HttpServletRequest request) throws IOException {

        log.info("Upload recebido: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        String docId = ingestUseCase.ingest(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getInputStream(),
                file.getSize()
        );

        String actor = auth != null ? auth.getName() : "anônimo";
        auditLogService.record(actor, AuditAction.DOCUMENT_UPLOAD, docId, "DOCUMENT",
                "arquivo=" + file.getOriginalFilename(), request.getRemoteAddr());

        return ResponseEntity.ok(new RestDtos.IngestResponse(docId,
                "Documento '" + file.getOriginalFilename() + "' indexado com sucesso."));
    }

    @GetMapping
    public ResponseEntity<List<RestDtos.DocumentResponse>> list() {
        List<RestDtos.DocumentResponse> docs = listUseCase.listAll()
                .stream()
                .map(RestDtos.DocumentResponse::from)
                .toList();
        return ResponseEntity.ok(docs);
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
