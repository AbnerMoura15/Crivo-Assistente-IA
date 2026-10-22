package com.ragbot.adapter.out.ai;

import com.ragbot.adapter.out.ai.groq.GroqLlmAdapter;
import com.ragbot.domain.entity.ChatMessage;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD #6 — Resiliência do GroqLlmAdapter.
 * Usa MockWebServer para simular respostas da API sem chamadas reais.
 *
 * Verifica: timeout, retry em 429/503, falha após esgotar retries.
 */
@DisplayName("GroqLlmAdapter — Resiliência")
class GroqLlmAdapterResilienceTest {

    private MockWebServer server;
    private GroqLlmAdapter adapter;

    private static final List<ChatMessage> HISTORY = List.of(
            new ChatMessage("user", "Pergunta de teste", Instant.now())
    );

    private static final String SUCCESS_BODY = """
            {
              "choices": [
                {"message": {"role": "assistant", "content": "Resposta correta"}}
              ]
            }
            """;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        adapter = new GroqLlmAdapter(
                server.url("/").toString(), // endpoint aponta para mock
                "fake-token",
                "llama-3.3-70b-versatile",
                5,    // timeoutSeconds
                3,    // maxRetries
                100   // initialBackoffMs — pequeno para testes serem rápidos
        );
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("resposta 200 bem formada deve retornar conteúdo")
    void successResponseShouldReturnContent() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(SUCCESS_BODY)
                .addHeader("Content-Type", "application/json"));

        String result = adapter.complete("Você é um assistente", HISTORY);

        assertThat(result).isEqualTo("Resposta correta");
    }

    @Test
    @DisplayName("429 NÃO deve ser retentado — falha imediata com RateLimitException")
    void shouldNotRetryOn429_failsImmediately() {
        // Só 1 resposta enfileirada — se houver retry, o servidor ficaria sem resposta
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("{\"error\":{\"message\":\"rate limit exceeded\"}}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> adapter.complete("Você é um assistente", HISTORY))
                .isInstanceOf(GroqLlmAdapter.RateLimitException.class)
                .hasMessageContaining("rate limit");

        // Exatamente 1 tentativa — sem retry!
        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("deve fazer retry em erro 503 (service unavailable) e ter sucesso")
    void shouldRetryOn503AndSucceed() {
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(SUCCESS_BODY)
                .addHeader("Content-Type", "application/json"));

        String result = adapter.complete("Você é um assistente", HISTORY);
        assertThat(result).isEqualTo("Resposta correta");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("deve lançar exceção após esgotar todos os retries em 503")
    void shouldThrowAfterExhaustingRetries() {
        // 3 retries configurados para 5xx — enfileira 4 falhas 503
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> adapter.complete("Você é um assistente", HISTORY))
                .isInstanceOf(RuntimeException.class);

        // 1 original + 3 retries = 4 tentativas
        assertThat(server.getRequestCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("erro 400 não deve ser retentado (erro do cliente)")
    void shouldNotRetryOn400() {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("{\"error\":{\"message\":\"bad request\"}}")
                .addHeader("Content-Type", "application/json"));

        assertThatThrownBy(() -> adapter.complete("Você é um assistente", HISTORY))
                .isInstanceOf(RuntimeException.class);

        // Apenas 1 tentativa — sem retry para erro do cliente
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}
