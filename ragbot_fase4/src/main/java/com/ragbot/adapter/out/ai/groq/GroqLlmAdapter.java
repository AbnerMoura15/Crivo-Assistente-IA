package com.ragbot.adapter.out.ai.groq;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ragbot.application.port.out.LlmPort;
import com.ragbot.domain.entity.ChatMessage;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Adapter para Groq — com timeout e retry com backoff exponencial (#6).
 *
 * TIMEOUT (default 30s): sem isso a thread trava indefinidamente.
 * RETRY (default 3x): erros 429/5xx são retentados com espera crescente.
 * NÃO retenta 4xx (exceto 429) — erros do cliente não vão melhorar sozinhos.
 */
@Component
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "groq")
public class GroqLlmAdapter implements LlmPort {

    private static final Logger log = LoggerFactory.getLogger(GroqLlmAdapter.class);
    private static final Set<Integer> RETRYABLE = Set.of(429, 500, 502, 503, 504);

    private final WebClient webClient;
    private final String model;
    private final Duration timeout;
    private final int maxRetries;
    private final long initialBackoffMs;

    public GroqLlmAdapter(
            @Value("${groq.endpoint:https://api.groq.com/openai/v1}") String endpoint,
            @Value("${groq.token:}") String token,
            @Value("${groq.model:llama-3.3-70b-versatile}") String model,
            @Value("${groq.timeout-seconds:30}") int timeoutSeconds,
            @Value("${groq.max-retries:3}") int maxRetries,
            @Value("${groq.initial-backoff-ms:500}") long initialBackoffMs) {

        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY nao configurada.");
        }

        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.maxRetries = maxRetries;
        this.initialBackoffMs = initialBackoffMs;

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000);

        this.webClient = WebClient.builder()
                .baseUrl(endpoint)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + token)
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> history) {
        List<MessageDto> messages = new ArrayList<>();
        messages.add(new MessageDto("system", systemPrompt));
        history.forEach(m -> messages.add(
                new MessageDto(m.role().equals("model") ? "assistant" : m.role(), m.content())));

        log.debug("Groq request: model={} messages={}", model, messages.size());

        ChatResponseDto response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(new ChatRequestDto(model, messages, 2048, 0.3))
                .retrieve()
                .onStatus(
                    s -> s.is4xxClientError() && s.value() != 429,
                    r -> r.bodyToMono(String.class).defaultIfEmpty("").flatMap(body ->
                            Mono.error(new WebClientResponseException(
                                    r.statusCode().value(), body, null, null, null)))
                )
                .onStatus(
                    s -> RETRYABLE.contains(s.value()),
                    r -> r.bodyToMono(String.class).defaultIfEmpty("").flatMap(body ->
                            Mono.error(new RetryableException(r.statusCode().value() + ": " + body)))
                )
                .bodyToMono(ChatResponseDto.class)
                .timeout(timeout)
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(initialBackoffMs))
                        .filter(t -> t instanceof RetryableException)
                        .doBeforeRetry(s -> log.warn("Groq retry {}/{}: {}",
                                s.totalRetries() + 1, maxRetries, s.failure().getMessage())))
                // Unwrap RetryExhaustedException → expõe a causa original (ex: "429: rate limit")
                .onErrorMap(Exceptions::isRetryExhausted, t -> {
                    Throwable cause = t.getCause();
                    return cause instanceof RuntimeException re ? re
                            : new RuntimeException("Groq retries esgotados", cause);
                })
                .block();

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new RuntimeException("Resposta invalida do Groq");
        }

        String answer = response.choices().get(0).message().content();
        if (answer == null || answer.isBlank()) throw new RuntimeException("Resposta do Groq vazia");

        log.debug("Groq respondeu: {} chars", answer.length());
        return answer;
    }

    static class RetryableException extends RuntimeException {
        RetryableException(String msg) { super(msg); }
    }

    record MessageDto(String role, String content) {}
    record ChatRequestDto(String model, List<MessageDto> messages,
                          @JsonProperty("max_tokens") int maxTokens, double temperature) {}
    record ChatResponseDto(List<ChoiceDto> choices) {}
    record ChoiceDto(MessageDto message) {}
}
