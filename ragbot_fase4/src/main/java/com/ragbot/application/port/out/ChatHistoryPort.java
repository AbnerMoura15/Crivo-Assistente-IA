package com.ragbot.application.port.out;

import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;

import java.util.List;
import java.util.Optional;

public interface ChatHistoryPort {
    ChatSession createSession(String username);
    Optional<ChatSession> findSession(String sessionId);
    List<ChatSession> findSessionsByUser(String username, int limit);
    void addMessage(String sessionId, ChatMessage message);
    List<ChatMessage> loadMessages(String sessionId);
}
