package com.ragbot.application.port.in;

import com.ragbot.domain.entity.ChatMessage;

import java.util.List;

public interface QueryChatUseCase {
    ChatResult query(String question, List<ChatMessage> history, String username);

    ChatResult queryWithSession(String question, String sessionId, String username);

    record ChatResult(String answer, String interactionId, String sessionId) {}
}
