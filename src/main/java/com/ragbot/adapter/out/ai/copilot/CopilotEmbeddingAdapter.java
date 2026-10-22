package com.ragbot.adapter.out.ai.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ragbot.application.port.out.EmbeddingPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;

@Component
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "copilot", matchIfMissing = true)
public class CopilotEmbeddingAdapter implements EmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(CopilotEmbeddingAdapter.class);

    private final WebClient webClient;
    private final String model;

    public CopilotEmbeddingAdapter(
            @Value("${copilot.endpoint}") String endpoint,
            @Value("${copilot.token}") String token,
            @Value("${copilot.embedding-model:text-embedding-3-small}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public List<Float> embed(String text) {
        log.debug("Gerando embedding para {} caracteres", text.length());

        EmbeddingResponseDto response = webClient.post()
                .uri("/embeddings")
                .bodyValue(new EmbeddingRequestDto(model, text))
                .retrieve()
                .bodyToMono(EmbeddingResponseDto.class)
                .block();

        if (response == null || response.data() == null || response.data().isEmpty()) {
            throw new RuntimeException("Resposta de embedding inválida");
        }

        return response.data().get(0).embedding();
    }

    record EmbeddingRequestDto(String model, String input) {}

    record EmbeddingResponseDto(List<EmbeddingDataDto> data) {}

    record EmbeddingDataDto(@JsonProperty("embedding") List<Float> embedding) {}
}
