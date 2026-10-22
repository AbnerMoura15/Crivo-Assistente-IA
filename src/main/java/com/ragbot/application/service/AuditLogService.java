package com.ragbot.application.service;

import com.ragbot.application.port.out.AuditLogPort;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.AuditEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogPort auditLogPort;

    public AuditLogService(AuditLogPort auditLogPort) {
        this.auditLogPort = auditLogPort;
    }

    public void record(String actor, AuditAction action, String targetId, String targetType,
                       String detail, String ipAddress) {
        try {
            AuditEntry entry = new AuditEntry(
                    UUID.randomUUID().toString(),
                    actor != null ? actor : "system",
                    action,
                    targetId,
                    targetType,
                    detail,
                    ipAddress,
                    Instant.now()
            );
            auditLogPort.save(entry);
        } catch (Exception e) {
            // Audit log nunca pode derrubar o fluxo principal
            log.error("Falha ao registrar audit log: {} {} — {}", actor, action, e.getMessage());
        }
    }

    public void record(String actor, AuditAction action, String detail) {
        record(actor, action, null, null, detail, null);
    }

    public List<AuditEntry> findRecent(int limit) {
        return auditLogPort.findRecent(limit);
    }

    public List<AuditEntry> findByActor(String actor, int limit) {
        return auditLogPort.findByActor(actor, limit);
    }

    public List<AuditEntry> findByAction(AuditAction action, int limit) {
        return auditLogPort.findByAction(action, limit);
    }
}
