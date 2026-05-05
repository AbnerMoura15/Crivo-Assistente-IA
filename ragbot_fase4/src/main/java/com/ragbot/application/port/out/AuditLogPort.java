package com.ragbot.application.port.out;

import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.AuditEntry;

import java.util.List;

public interface AuditLogPort {
    void save(AuditEntry entry);
    List<AuditEntry> findRecent(int limit);
    List<AuditEntry> findByActor(String actor, int limit);
    List<AuditEntry> findByAction(AuditAction action, int limit);
}
