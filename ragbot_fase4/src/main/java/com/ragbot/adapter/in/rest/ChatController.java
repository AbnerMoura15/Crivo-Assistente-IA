package com.ragbot.adapter.in.rest;

import com.ragbot.adapter.in.rest.dto.RestDtos;
import com.ragbot.application.port.in.QueryChatUseCase;
import com.ragbot.application.service.ChatHistoryService;
import com.ragbot.domain.entity.Namespace;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final QueryChatUseCase queryChatUseCase;
    private final ChatHistoryService chatHistoryService;

    public ChatController(QueryChatUseCase queryChatUseCase, ChatHistoryService chatHistoryService) {
        this.queryChatUseCase = queryChatUseCase;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping
    public ResponseEntity<RestDtos.ChatResponse> chat(
            @RequestBody RestDtos.ChatRequest request,
            Authentication auth) {

        String username = auth != null ? auth.getName() : "anônimo";
        String namespaceId = request.namespaceId() != null
                ? request.namespaceId()
                : Namespace.DEFAULT_ID;

        QueryChatUseCase.ChatResult result = queryChatUseCase.queryWithSession(
                request.question(), request.sessionId(), username, namespaceId);

        return ResponseEntity.ok(new RestDtos.ChatResponse(
                result.answer(), result.interactionId(), result.sessionId()));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<RestDtos.ChatSessionResponse>> listSessions(Authentication auth) {
        String username = auth.getName();
        List<RestDtos.ChatSessionResponse> sessions = chatHistoryService.listUserSessions(username)
                .stream()
                .map(RestDtos.ChatSessionResponse::from)
                .toList();
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<RestDtos.ChatSessionWithMessagesResponse> getSession(
            @PathVariable String sessionId,
            Authentication auth) {

        return chatHistoryService.getSessionWithMessages(sessionId)
                .filter(s -> s.username().equals(auth.getName()))
                .map(s -> ResponseEntity.ok(RestDtos.ChatSessionWithMessagesResponse.from(s)))
                .orElse(ResponseEntity.notFound().build());
    }
}
