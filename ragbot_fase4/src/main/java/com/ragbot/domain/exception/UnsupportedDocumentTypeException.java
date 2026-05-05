package com.ragbot.domain.exception;

public class UnsupportedDocumentTypeException extends RuntimeException {
    public UnsupportedDocumentTypeException(String mimeType) {
        super("Tipo de documento não suportado: " + mimeType);
    }
}
