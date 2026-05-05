package com.ragbot.domain.entity;

import java.time.Instant;

public record Document(
        String id,
        String fileName,
        String mimeType,
        String storageReference,
        long fileSizeBytes,
        Instant ingestedAt
) {}
