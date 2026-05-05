package com.ragbot.domain.entity;

import java.util.List;
import java.util.Map;

public record Chunk(
        String id,
        String documentId,
        String content,
        List<Float> embedding,
        Map<String, String> metadata
) {}
