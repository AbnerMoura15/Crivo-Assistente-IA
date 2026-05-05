package com.ragbot.application.port.in;

import java.io.InputStream;

public interface IngestDocumentUseCase {
    String ingest(String fileName, String mimeType, InputStream content, long sizeBytes);
}
