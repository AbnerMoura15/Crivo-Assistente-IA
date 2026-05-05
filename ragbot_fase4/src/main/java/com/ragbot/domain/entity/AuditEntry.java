package com.ragbot.domain.entity;

import java.time.Instant;

public record AuditEntry(
        String id,
        String actor,
        AuditAction action,
        String targetId,
        String targetType,
        String detail,
        String ipAddress,
        Instant createdAt
) {}
