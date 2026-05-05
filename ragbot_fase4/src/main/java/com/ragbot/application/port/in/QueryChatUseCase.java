package com.ragbot.application.port.in;

import com.ragbot.domain.entity.ChatMessage;

import java.util.List;

public interface QueryChatUseCase {
    String query(String question, List<ChatMessage> history);
}
