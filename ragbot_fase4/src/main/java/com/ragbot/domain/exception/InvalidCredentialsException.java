package com.ragbot.domain.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Usuário ou senha inválidos");
    }
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
