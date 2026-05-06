package com.ragbot.application.port.in;

import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.Namespace;

import java.util.List;

public interface QueryChatUseCase {

    /**
     * Consulta com histórico explícito, escopada ao namespace (#33).
     */
    ChatResult query(String question, List<ChatMessage> history,
                     String username, String namespaceId);

    /** Compatibilidade retroativa — usa namespace padrão. */
    default ChatResult query(String question, List<ChatMessage> history, String username) {
        return query(question, history, username, Namespace.DEFAULT_ID);
    }

    /**
     * Consulta com sessão persistida, escopada ao namespace (#33).
     */
    ChatResult queryWithSession(String question, String sessionId,
                                String username, String namespaceId);

    /** Compatibilidade retroativa — usa namespace padrão. */
    default ChatResult queryWithSession(String question, String sessionId, String username) {
        return queryWithSession(question, sessionId, username, Namespace.DEFAULT_ID);
    }

    record ChatResult(String answer, String interactionId, String sessionId, List<String> sources) {
        /** Compat para chamadas que não precisam de sources (testes, fallbacks). */
        public ChatResult(String answer, String interactionId, String sessionId) {
            this(answer, interactionId, sessionId, List.of());
        }
    }
}
