package com.ragbot.adapter.in.rest;

import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.RagObservabilityService;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.AuditEntry;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AuditLogService auditLogService;
    private final RagObservabilityService observabilityService;

    public AdminController(AuditLogService auditLogService,
                           RagObservabilityService observabilityService) {
        this.auditLogService = auditLogService;
        this.observabilityService = observabilityService;
    }

    /** GET /api/admin/dashboard — métricas de qualidade RAG (#10) */
    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard() {
        return ResponseEntity.ok(observabilityService.getMetrics());
    }

    /** GET /api/admin/audit-log — log de auditoria (#11) */
    @GetMapping("/audit-log")
    public ResponseEntity<List<AuditEntryResponse>> auditLog(
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action) {

        List<AuditEntry> entries;
        if (actor != null && !actor.isBlank()) {
            entries = auditLogService.findByActor(actor, limit);
        } else if (action != null && !action.isBlank()) {
            entries = auditLogService.findByAction(AuditAction.valueOf(action.toUpperCase()), limit);
        } else {
            entries = auditLogService.findRecent(limit);
        }

        return ResponseEntity.ok(entries.stream().map(AuditEntryResponse::from).toList());
    }

    record AuditEntryResponse(
            String id, String actor, String action,
            String targetId, String targetType,
            String detail, String ipAddress, Instant createdAt
    ) {
        static AuditEntryResponse from(AuditEntry e) {
            return new AuditEntryResponse(
                    e.id(), e.actor(), e.action().name(),
                    e.targetId(), e.targetType(),
                    e.detail(), e.ipAddress(), e.createdAt()
            );
        }
    }
}
