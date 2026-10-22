package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.adapter.out.ai.groq.GroqLlmAdapter;
import com.ragbot.domain.exception.DocumentNotFoundException;
import com.ragbot.domain.exception.InvalidCredentialsException;
import com.ragbot.domain.exception.TokenLimitExceededException;
import com.ragbot.domain.exception.UnsupportedDocumentTypeException;
import com.ragbot.domain.exception.UserAlreadyExistsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
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

    /** Rate limit da API Groq (429) — mensagem amigável, sem retry. */
    @ExceptionHandler(GroqLlmAdapter.RateLimitException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleGroqRateLimit(GroqLlmAdapter.RateLimitException e) {
        log.warn("Rate limit Groq: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(new RestDtos.ErrorResponse("GROQ_RATE_LIMIT",
                        "Limite de requisições da IA atingido. Aguarde alguns segundos e tente novamente."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new RestDtos.ErrorResponse("UNSUPPORTED_MEDIA_TYPE", "Content-Type não suportado."));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RestDtos.ErrorResponse("INVALID_BODY", "Corpo da requisição inválido."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(new RestDtos.ErrorResponse("FILE_TOO_LARGE", "Arquivo excede o tamanho máximo permitido."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new RestDtos.ErrorResponse("FORBIDDEN", "Acesso negado."));
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    public ResponseEntity<RestDtos.ErrorResponse> handleNoRoute(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new RestDtos.ErrorResponse("NOT_FOUND", "Recurso não encontrado."));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleMissingParam(MissingServletRequestParameterException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new RestDtos.ErrorResponse("MISSING_PARAMETER",
                        "Parâmetro obrigatório ausente: " + e.getParameterName()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestDtos.ErrorResponse> handleGeneral(Exception e) {
        // Detalhe completo só no log (correlacionado pelo requestId); o cliente recebe mensagem genérica
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new RestDtos.ErrorResponse("INTERNAL_ERROR", "Erro interno. Tente novamente mais tarde."));
    }
}
