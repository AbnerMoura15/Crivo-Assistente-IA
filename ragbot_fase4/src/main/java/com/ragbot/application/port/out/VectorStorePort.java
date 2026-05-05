package com.ragbot.application.port.out;

import com.ragbot.domain.entity.Chunk;

import java.util.List;

public interface VectorStorePort {
    void save(Chunk chunk);
    List<Chunk> findSimilar(List<Float> queryEmbedding, int topK);
    void deleteByDocumentId(String documentId);
    long countByDocumentId(String documentId);
}
