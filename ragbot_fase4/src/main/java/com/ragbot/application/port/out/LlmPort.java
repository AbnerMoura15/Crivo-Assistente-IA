package com.ragbot.application.port.out;

import com.ragbot.domain.entity.ChatMessage;

import java.util.List;

public interface LlmPort {
    String complete(String systemPrompt, List<ChatMessage> history);
}
