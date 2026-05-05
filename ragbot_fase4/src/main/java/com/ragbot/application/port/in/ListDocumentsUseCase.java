package com.ragbot.application.port.in;

import com.ragbot.domain.entity.Document;

import java.util.List;

public interface ListDocumentsUseCase {
    List<Document> listAll();
    void delete(String documentId);
}
