package com.ragbot.application.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingStrategyTest {

    private final ChunkingStrategy strategy = new ChunkingStrategy(5, 1);

    @Test
    void shouldReturnEmptyForNullText() {
        assertThat(strategy.split(null)).isEmpty();
    }

    @Test
    void shouldReturnSingleChunkForShortText() {
        List<String> chunks = strategy.split("um dois tres");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).isEqualTo("um dois tres");
    }

    @Test
    void shouldSplitWithOverlap() {
        // chunkSize=5, overlap=1
        // words: [a, b, c, d, e, f, g]
        List<String> chunks = strategy.split("a b c d e f g");
        assertThat(chunks).hasSize(2);
        // Primeiro chunk: a b c d e
        assertThat(chunks.get(0)).isEqualTo("a b c d e");
        // Segundo chunk começa em index 4 (5-1): e f g
        assertThat(chunks.get(1)).isEqualTo("e f g");
    }
}
