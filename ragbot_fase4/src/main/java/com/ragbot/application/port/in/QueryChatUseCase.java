package com.ragbot.application.port.in;

import com.ragbot.domain.entity.ChatMessage;

import java.util.List;

public interface QueryChatUseCase {
    /**
     * @return ChatResult com a resposta e o ID da interação (para feedback)
     */
    ChatResult query(String question, List<ChatMessage> history, String username);

    record ChatResult(String answer, String interactionId) {}
}
