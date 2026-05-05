package com.ragbot.application.port.out;

import com.ragbot.domain.entity.Document;

import java.io.InputStream;
import java.util.List;

public interface DocumentStoragePort {
    String store(String fileName, String mimeType, long sizeBytes, InputStream content);
    InputStream retrieve(String documentId);
    List<Document> findAll();
    void delete(String documentId);
}
