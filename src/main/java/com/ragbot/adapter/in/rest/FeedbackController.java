package com.ragbot.adapter.in.rest;

import com.ragbot.application.service.RagObservabilityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Controller #9 — Feedback 👍/👎 por interação RAG.
 *
 * O frontend envia o ID da interação (retornado no campo interactionId
 * da resposta do chat) junto com a avaliação do usuário.
 *
 * Endpoint: POST /api/feedback
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final RagObservabilityService observabilityService;

    public FeedbackController(RagObservabilityService observabilityService) {
        this.observabilityService = observabilityService;
    }

    @PostMapping
    public ResponseEntity<Void> recordFeedback(
            @Valid @RequestBody FeedbackRequest request,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "anônimo";
        observabilityService.recordFeedback(request.interactionId(), request.positive(), username);
        return ResponseEntity.noContent().build();
    }

    record FeedbackRequest(
            @NotBlank(message = "interactionId é obrigatório") String interactionId,
            @NotNull(message = "positive é obrigatório")       Boolean positive
    ) {}
}
