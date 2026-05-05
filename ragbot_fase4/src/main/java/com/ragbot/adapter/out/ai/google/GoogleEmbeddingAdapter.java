package com.ragbot.adapter.out.ai.google;

import com.ragbot.application.port.out.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "groq")
public class GoogleEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(GoogleEmbeddingAdapter.class);

    private final WebClient webClient;
    private final String model;
    private final String token;

    public GoogleEmbeddingAdapter(
            @Value("${google.embedding-endpoint:https://generativelanguage.googleapis.com/v1beta}") String endpoint,
            @Value("${google.token:}") String token,
            @Value("${google.embedding-model:gemini-embedding-001}") String model) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GOOGLE_API_KEY nao configurada. Defina a variavel de ambiente GOOGLE_API_KEY para gerar embeddings.");
        }
        this.token = token;
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("x-goog-api-key", token)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        log.debug("Gerando embedding Google para {} caracteres", text.length());

        EmbedResponseDto response = webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:embedContent")
                        .queryParam("key", token)
                        .build(normalizeModel(model)))
                .bodyValue(new EmbedRequestDto(
                        "RETRIEVAL_QUERY",
                        new ContentDto(List.of(new PartDto(text)))
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, clientResponse ->
                        clientResponse.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .flatMap(body -> Mono.error(new RuntimeException(
                                        "Google Embedding API error " + clientResponse.statusCode() + ": " + body))))
                .bodyToMono(EmbedResponseDto.class)
                .block();

        if (response == null || response.embedding() == null || response.embedding().values() == null || response.embedding().values().isEmpty()) {
            throw new RuntimeException("Resposta de embedding Google invalida");
        }

        return response.embedding().values();
    }

    private String normalizeModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return "gemini-embedding-001";
        }
        return modelName.startsWith("models/") ? modelName.substring("models/".length()) : modelName;
    }

    record EmbedRequestDto(String taskType, ContentDto content) {}

    record ContentDto(List<PartDto> parts) {}

    record PartDto(String text) {}

    record EmbedResponseDto(EmbeddingDto embedding) {}

    record EmbeddingDto(List<Float> values) {}
}
