package com.ragbot.domain.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String field, String value) {
        super("Já existe um usuário com este " + field + ": " + value);
    }
}
