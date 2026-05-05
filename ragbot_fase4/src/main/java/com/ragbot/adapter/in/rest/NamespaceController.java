package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.application.port.in.ManageNamespacesUseCase;
import com.ragbot.application.service.AuditLogService;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.Namespace;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller para gerenciamento de namespaces (#33).
 *
 * Endpoints públicos (leitura — qualquer usuário autenticado):
 *   GET  /api/namespaces
 *
 * Endpoints admin:
 *   POST   /api/namespaces
 *   DELETE /api/namespaces/{id}
 */
@RestController
@RequestMapping("/api/namespaces")
public class NamespaceController {

    private final ManageNamespacesUseCase manageNamespacesUseCase;
    private final AuditLogService auditLogService;

    public NamespaceController(ManageNamespacesUseCase manageNamespacesUseCase,
                               AuditLogService auditLogService) {
        this.manageNamespacesUseCase = manageNamespacesUseCase;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RestDtos.NamespaceResponse>> listAll() {
        List<RestDtos.NamespaceResponse> namespaces = manageNamespacesUseCase.listAll()
                .stream().map(RestDtos.NamespaceResponse::from).toList();
        return ResponseEntity.ok(namespaces);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RestDtos.NamespaceResponse> create(
            @Valid @RequestBody CreateNamespaceRequest request,
            Authentication auth,
            HttpServletRequest httpRequest) {
        Namespace created = manageNamespacesUseCase.create(request.slug(), request.displayName());
        auditLogService.record(auth.getName(), AuditAction.NAMESPACE_CREATE, created.id(),
                "NAMESPACE", "slug=" + created.slug(), httpRequest.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.CREATED).body(RestDtos.NamespaceResponse.from(created));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable String id,
            Authentication auth,
            HttpServletRequest httpRequest) {
        manageNamespacesUseCase.delete(id);
        auditLogService.record(auth.getName(), AuditAction.NAMESPACE_DELETE, id,
                "NAMESPACE", null, httpRequest.getRemoteAddr());
        return ResponseEntity.noContent().build();
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    record CreateNamespaceRequest(
            @NotBlank
            @Pattern(regexp = "[a-z0-9-]+", message = "Slug deve conter apenas letras minúsculas, números e hífen")
            String slug,
            @NotBlank String displayName
    ) {}
}
