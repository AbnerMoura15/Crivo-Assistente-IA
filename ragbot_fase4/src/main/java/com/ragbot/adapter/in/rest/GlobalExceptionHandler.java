package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.domain.exception.DocumentNotFoundException;
import com.ragbot.domain.exception.InvalidCredentialsException;
import com.ragbot.domain.exception.TokenLimitExceededException;
import com.ragbot.domain.exception.UnsupportedDocumentTypeException;
import com.ragbot.domain.exception.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new RestDtos.ErrorResponse("INVALID_CREDENTIALS", e.getMessage()));
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new RestDtos.ErrorResponse("USER_ALREADY_EXISTS", e.getMessage()));
    }

    @ExceptionHandler(UnsupportedDocumentTypeException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleUnsupportedType(UnsupportedDocumentTypeException e) {
        log.warn("Tipo nao suportado: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new RestDtos.ErrorResponse("UNSUPPORTED_TYPE", e.getMessage()));
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleNotFound(DocumentNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestDtos.ErrorResponse("NOT_FOUND", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RestDtos.ErrorResponse("VALIDATION_ERROR", msg));
    }

    @ExceptionHandler(TokenLimitExceededException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleTokenLimit(TokenLimitExceededException e) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new RestDtos.ErrorResponse("TOKEN_LIMIT_EXCEEDED", e.getMessage()));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new RestDtos.ErrorResponse("UNSUPPORTED_MEDIA_TYPE", e.getMessage()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RestDtos.ErrorResponse("MISSING_PARAMETER",
                        "Parâmetro obrigatório ausente: " + e.getParameterName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleGeneral(Exception e) {
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RestDtos.ErrorResponse("INTERNAL_ERROR", e.getMessage()));
    }
}
