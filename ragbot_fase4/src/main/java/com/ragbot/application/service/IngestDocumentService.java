package com.ragbot.application.service;

import com.ragbot.application.port.in.IngestDocumentUseCase;
import com.ragbot.application.port.out.*;
import com.ragbot.domain.entity.Chunk;
import com.ragbot.domain.exception.UnsupportedDocumentTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class IngestDocumentService implements IngestDocumentUseCase {

    private static final Logger log = LoggerFactory.getLogger(IngestDocumentService.class);

    private final List<DocumentParserPort> parsers;
    private final EmbeddingPort embeddingPort;
    private final VectorStorePort vectorStorePort;
    private final DocumentStoragePort storagePort;
    private final ChunkingStrategy chunkingStrategy;

    public IngestDocumentService(
            List<DocumentParserPort> parsers,
            EmbeddingPort embeddingPort,
            VectorStorePort vectorStorePort,
            DocumentStoragePort storagePort,
            ChunkingStrategy chunkingStrategy) {
        this.parsers = parsers;
        this.embeddingPort = embeddingPort;
        this.vectorStorePort = vectorStorePort;
        this.storagePort = storagePort;
        this.chunkingStrategy = chunkingStrategy;
    }

    @Override
    public String ingest(String fileName, String mimeType, InputStream content, long sizeBytes) {
        log.info("Iniciando ingestão: {} ({})", fileName, mimeType);

        DocumentParserPort parser = parsers.stream()
                .filter(p -> p.supports(mimeType))
                .findFirst()
                .orElseThrow(() -> new UnsupportedDocumentTypeException(mimeType));

        String documentId = storagePort.store(fileName, mimeType, sizeBytes, content);
        log.info("Documento armazenado com ID: {}", documentId);

        InputStream stored = storagePort.retrieve(documentId);
        String rawText = parser.extractText(stored, fileName);
        log.info("Texto extraído: {} caracteres", rawText.length());

        List<String> chunks = chunkingStrategy.split(rawText);
        log.info("Dividido em {} chunks", chunks.size());

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Float> embedding = embeddingPort.embed(chunkText);
            vectorStorePort.save(new Chunk(
                    UUID.randomUUID().toString(),
                    documentId,
                    chunkText,
                    embedding,
                    Map.of(
                            "source", fileName,
                            "chunkIndex", String.valueOf(i),
                            "totalChunks", String.valueOf(chunks.size())
                    )
            ));
        }

        log.info("Ingestão concluída: {} chunks indexados para documentId={}", chunks.size(), documentId);
        return documentId;
    }
}
