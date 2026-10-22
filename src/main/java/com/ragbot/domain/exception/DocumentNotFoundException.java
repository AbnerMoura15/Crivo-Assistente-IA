package com.ragbot.domain.exception;

public class DocumentNotFoundException extends RuntimeException {
    public DocumentNotFoundException(String id) {
        super("Documento não encontrado: " + id);
    }
}
