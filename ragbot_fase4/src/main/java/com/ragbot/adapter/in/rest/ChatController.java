package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.domain.entity.ChatMessage;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final QueryChatUseCase queryChatUseCase;

    public ChatController(QueryChatUseCase queryChatUseCase) {
        this.queryChatUseCase = queryChatUseCase;
    }

    @PostMapping
    public ResponseEntity<RestDtos.ChatResponse> chat(@RequestBody RestDtos.ChatRequest request) {
        List<ChatMessage> history = request.history() == null ? List.of() :
                request.history().stream()
                        .map(m -> new ChatMessage(m.role(), m.content(), java.time.Instant.now()))
                        .toList();

        String answer = queryChatUseCase.query(request.question(), history);
        return ResponseEntity.ok(new RestDtos.ChatResponse(answer));
    }
}
