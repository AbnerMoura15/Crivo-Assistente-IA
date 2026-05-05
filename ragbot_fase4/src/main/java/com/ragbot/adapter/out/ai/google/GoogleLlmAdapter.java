package com.ragbot.adapter.out.ai.google;

import com.ragbot.application.port.out.LlmPort;
import com.ragbot.domain.entity.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "google")
public class GoogleLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(GoogleLlmAdapter.class);

    private final WebClient webClient;
    private final String model;
    private final String token;

    public GoogleLlmAdapter(
            @Value("${google.llm-endpoint:https://generativelanguage.googleapis.com/v1beta}") String endpoint,
            @Value("${google.token}") String token,
            @Value("${google.model:gemini-2.5-flash}") String model) {
        this.model = model;
        this.token = token;
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("x-goog-api-key", token)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> history) {
        List<ContentDto> contents = new ArrayList<>();
        for (ChatMessage message : history) {
            String role = normalizeRole(message.role());
            if (contents.isEmpty() || !contents.get(contents.size() - 1).role().equals(role)) {
                contents.add(new ContentDto(role, new ArrayList<>()));
            }
            contents.get(contents.size() - 1).parts().add(new PartDto(message.content()));
        }

        GenerateContentResponse response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", token)
                        .build(normalizeModel(model)))
                .bodyValue(new GenerateContentRequest(
                        new InstructionDto(List.of(new PartDto(systemPrompt))),
                        contents
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Google LLM API error " + clientResponse.statusCode() + ": " + body))))
                .bodyToMono(GenerateContentResponse.class)
                .block();

        if (response == null || response.candidates() == null || response.candidates().isEmpty()) {
            throw new RuntimeException("Resposta invalida do Google LLM");
        }

        CandidateDto first = response.candidates().get(0);
        if (first.content() == null || first.content().parts() == null || first.content().parts().isEmpty()) {
            throw new RuntimeException("Resposta do Google LLM sem conteudo");
        }

        String answer = first.content().parts().stream()
                .map(PartDto::text)
                .filter(t -> t != null && !t.isBlank())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);

        if (answer.isBlank()) {
            throw new RuntimeException("Resposta do Google LLM vazia");
        }

        log.debug("Resposta Google LLM com {} chars", answer.length());
        return answer;
    }

    private String normalizeModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "gemini-2.5-flash";
        }
        return modelName.startsWith("models/") ? modelName.substring("models/".length()) : modelName;
    }

    private String normalizeRole(String role) {
        if (role == null) {
            return "user";
        }

        return switch (role.toLowerCase(Locale.ROOT)) {
            case "assistant", "model" -> "model";
            default -> "user";
        };
    }

    record GenerateContentRequest(InstructionDto systemInstruction, List<ContentDto> contents) {}

    record InstructionDto(List<PartDto> parts) {}

    record ContentDto(String role, List<PartDto> parts) {}

    record PartDto(String text) {}

    record GenerateContentResponse(List<CandidateDto> candidates) {}

    record CandidateDto(ContentDto content) {}
}
