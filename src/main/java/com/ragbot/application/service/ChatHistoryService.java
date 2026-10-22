package com.ragbot.application.service;

import com.ragbot.application.port.out.ChatHistoryPort;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);

    private final ChatHistoryPort   chatHistoryPort;
    private final RagConfigService  ragConfigService;

    /** Construtor principal — limite de histórico lido dinamicamente via RagConfig. */
    @Autowired
    public ChatHistoryService(ChatHistoryPort chatHistoryPort, RagConfigService ragConfigService) {
        this.chatHistoryPort  = chatHistoryPort;
        this.ragConfigService = ragConfigService;
    }

    /** Construtor de compatibilidade para testes antigos sem RagConfigService. */
    public ChatHistoryService(ChatHistoryPort chatHistoryPort) {
        this(chatHistoryPort, null);
    }

    public ChatSession getOrCreateSession(String sessionId, String username) {
        if (sessionId != null && !sessionId.isBlank()) {
            Optional<ChatSession> existing = chatHistoryPort.findSession(sessionId)
                    .filter(s -> s.username().equals(username));   // nunca reutiliza sessão de outro usuário
            if (existing.isPresent()) {
                return existing.get();
            }
            log.warn("Sessão {} não encontrada (ou de outro usuário) para '{}', criando nova", sessionId, username);
        }
        return chatHistoryPort.createSession(username);
    }

    public List<ChatMessage> loadRecentMessages(String sessionId) {
        int max = (ragConfigService != null)
                ? ragConfigService.getConfig().maxHistoryMessages()
                : 6;
        List<ChatMessage> all = chatHistoryPort.loadMessages(sessionId);
        if (all.size() <= max) return all;
        return all.subList(all.size() - max, all.size());
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

    /**
     * Remove uma sessão e todas as suas mensagens.
     * Retorna true se a sessão pertencia ao usuário e foi removida;
     * false se não existia ou pertencia a outro usuário.
     */
    public boolean deleteSession(String sessionId, String username) {
        return chatHistoryPort.findSession(sessionId)
                .filter(s -> s.username().equals(username))
                .map(s -> {
                    chatHistoryPort.deleteSession(sessionId);
                    log.info("Sessão {} removida pelo usuário '{}'", sessionId, username);
                    return true;
                })
                .orElse(false);
    }
}
