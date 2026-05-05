package com.ragbot.application.service;

import com.ragbot.application.port.out.ChatHistoryPort;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final int MAX_HISTORY_MESSAGES = 6;

    private final ChatHistoryPort chatHistoryPort;

    public ChatHistoryService(ChatHistoryPort chatHistoryPort) {
        this.chatHistoryPort = chatHistoryPort;
    }

    public ChatSession getOrCreateSession(String sessionId, String username) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ChatSession> existing = chatHistoryPort.findSession(sessionId);
            if (existing.isPresent()) {
                return existing.get();
            }
            log.warn("Sessão {} não encontrada para '{}', criando nova", sessionId, username);
        }
        return chatHistoryPort.createSession(username);
    }

    public List<ChatMessage> loadRecentMessages(String sessionId) {
        List<ChatMessage> all = chatHistoryPort.loadMessages(sessionId);
        if (all.size() <= MAX_HISTORY_MESSAGES) return all;
        return all.subList(all.size() - MAX_HISTORY_MESSAGES, all.size());
    }

    public void saveUserMessage(String sessionId, String content) {
        chatHistoryPort.addMessage(sessionId, ChatMessage.user(content));
    }

    public void saveAssistantMessage(String sessionId, String content) {
        chatHistoryPort.addMessage(sessionId, ChatMessage.assistant(content));
    }

    public List<ChatSession> listUserSessions(String username) {
        return chatHistoryPort.findSessionsByUser(username, 20);
    }

    public Optional<ChatSession> getSessionWithMessages(String sessionId) {
        return chatHistoryPort.findSession(sessionId)
                .map(s -> s.withMessages(chatHistoryPort.loadMessages(sessionId)));
    }
}
