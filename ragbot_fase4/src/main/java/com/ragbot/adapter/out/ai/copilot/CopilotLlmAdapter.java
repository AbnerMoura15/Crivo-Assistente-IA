package com.ragbot.adapter.out.ai.copilot;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ragbot.application.port.out.LlmPort;
import com.ragbot.domain.entity.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Adapter para GitHub Copilot / Azure OpenAI.
 * Para trocar de provedor: mude ragbot.ai.provider no application.yml.
 */
@Component
@Primary
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "copilot", matchIfMissing = true)
public class CopilotLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(CopilotLlmAdapter.class);

    private final WebClient webClient;
    private final String model;

    public CopilotLlmAdapter(
            @Value("${copilot.endpoint}") String endpoint,
            @Value("${copilot.token}") String token,
            @Value("${copilot.model:gpt-4o}") String model) {
        this.model = model;
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> history) {
        List<MessageDto> messages = new ArrayList<>();
        messages.add(new MessageDto("system", systemPrompt));
        history.forEach(m -> messages.add(new MessageDto(m.role(), m.content())));

        ChatRequestDto request = new ChatRequestDto(model, messages, 2000, 0.3);

        log.debug("Chamando LLM com {} mensagens", messages.size());

        ChatResponseDto response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(ChatResponseDto.class)
                .block();

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("Resposta inválida do LLM");
        }

        return response.choices().get(0).message().content();
    }

    record MessageDto(String role, String content) {}

    record ChatRequestDto(
            String model,
            List<MessageDto> messages,
            @JsonProperty("max_tokens") int maxTokens,
            double temperature) {}

    record ChatResponseDto(List<ChoiceDto> choices) {}

    record ChoiceDto(MessageDto message) {}
}
