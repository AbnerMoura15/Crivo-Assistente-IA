package com.ragbot.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class ChunkingStrategy {

    private final int chunkSize;
    private final int overlap;

    public ChunkingStrategy(
            @Value("${ragbot.chunking.size:500}") int chunkSize,
            @Value("${ragbot.chunking.overlap:50}") int overlap) {
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();

        String normalized = text.replaceAll("\\s+", " ").trim();
        String[] words = normalized.split(" ");

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            String chunk = String.join(" ", Arrays.copyOfRange(words, start, end));
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end == words.length) break;
            start = end - overlap;
        }

        return chunks;
    }
}
