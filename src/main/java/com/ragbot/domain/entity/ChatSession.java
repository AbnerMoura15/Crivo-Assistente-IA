package com.ragbot.domain.entity;

import java.time.Instant;
import java.util.List;

public record ChatSession(
        String id,
        String username,
        Instant createdAt,
        Instant lastMessageAt,
        List<ChatMessage> messages
) {
    public ChatSession withMessages(List<ChatMessage> msgs) {
        return new ChatSession(id, username, createdAt, lastMessageAt, msgs);
    }
}
