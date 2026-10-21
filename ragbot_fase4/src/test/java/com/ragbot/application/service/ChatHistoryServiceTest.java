package com.ragbot.application.service;

import com.ragbot.application.port.out.ChatHistoryPort;
import com.ragbot.application.port.out.RagConfigPort;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;
import com.ragbot.domain.entity.RagConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ChatHistoryService")
class ChatHistoryServiceTest {

    @Mock ChatHistoryPort chatHistoryPort;

    ChatHistoryService service;

    private static final ChatSession SESSION = new ChatSession(
            "sess-1", "alice", Instant.now(), Instant.now(), List.of());

    @BeforeEach
    void setUp() {
        // #25/#16-hist — maxHistoryMessages = 6 via RagConfigService mockado
        RagConfigPort configPort = mock(RagConfigPort.class);
        when(configPort.load()).thenReturn(new RagConfig(3, 0.10, 80, true, 0.85, 100_000L, 6));
        RagConfigService ragConfigService = new RagConfigService(configPort);
        service = new ChatHistoryService(chatHistoryPort, ragConfigService);
    }

    @Test
    @DisplayName("getOrCreateSession sem sessionId deve criar nova sessão")
    void getOrCreateSession_nullId_createsNew() {
        when(chatHistoryPort.createSession("alice")).thenReturn(SESSION);

        ChatSession result = service.getOrCreateSession(null, "alice");

        assertThat(result.id()).isEqualTo("sess-1");
        verify(chatHistoryPort).createSession("alice");
        verify(chatHistoryPort, never()).findSession(any());
    }

    @Test
    @DisplayName("getOrCreateSession com sessionId existente deve reusar")
    void getOrCreateSession_existingId_reuses() {
        when(chatHistoryPort.findSession("sess-1")).thenReturn(Optional.of(SESSION));

        ChatSession result = service.getOrCreateSession("sess-1", "alice");

        assertThat(result.id()).isEqualTo("sess-1");
        verify(chatHistoryPort, never()).createSession(any());
    }

    @Test
    @DisplayName("getOrCreateSession com sessionId inexistente deve criar nova")
    void getOrCreateSession_unknownId_createsNew() {
        when(chatHistoryPort.findSession("ghost")).thenReturn(Optional.empty());
        when(chatHistoryPort.createSession("alice")).thenReturn(SESSION);

        ChatSession result = service.getOrCreateSession("ghost", "alice");

        assertThat(result.id()).isEqualTo("sess-1");
        verify(chatHistoryPort).createSession("alice");
    }

    @Test
    @DisplayName("getOrCreateSession com sessionId de OUTRO usuário cria nova sessão (não vaza histórico)")
    void getOrCreateSession_otherUsersSession_createsNew() {
        ChatSession bobSession = new ChatSession("sess-bob", "bob", Instant.now(), Instant.now(), List.of());
        when(chatHistoryPort.findSession("sess-1")).thenReturn(Optional.of(SESSION)); // pertence a alice
        when(chatHistoryPort.createSession("bob")).thenReturn(bobSession);

        ChatSession result = service.getOrCreateSession("sess-1", "bob");

        assertThat(result.id()).isEqualTo("sess-bob");
        assertThat(result.username()).isEqualTo("bob");
        verify(chatHistoryPort).createSession("bob");
    }

    @Test
    @DisplayName("loadRecentMessages deve retornar últimas 6 mensagens quando há mais")
    void loadRecentMessages_moreThanMax_returnsTruncated() {
        List<ChatMessage> msgs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            msgs.add(new ChatMessage("user", "msg " + i, Instant.now()));
        }
        when(chatHistoryPort.loadMessages("sess-1")).thenReturn(msgs);

        List<ChatMessage> result = service.loadRecentMessages("sess-1");

        assertThat(result).hasSize(6);
        assertThat(result.get(0).content()).isEqualTo("msg 4");
        assertThat(result.get(5).content()).isEqualTo("msg 9");
    }

    @Test
    @DisplayName("loadRecentMessages com menos de 6 retorna todas")
    void loadRecentMessages_fewerThanMax_returnsAll() {
        List<ChatMessage> msgs = List.of(
                new ChatMessage("user", "a", Instant.now()),
                new ChatMessage("assistant", "b", Instant.now())
        );
        when(chatHistoryPort.loadMessages("sess-1")).thenReturn(msgs);

        List<ChatMessage> result = service.loadRecentMessages("sess-1");

        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("saveUserMessage deve persistir mensagem com role 'user'")
    void saveUserMessage_persistsCorrectRole() {
        service.saveUserMessage("sess-1", "Olá!");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatHistoryPort).addMessage(eq("sess-1"), captor.capture());
        assertThat(captor.getValue().role()).isEqualTo("user");
        assertThat(captor.getValue().content()).isEqualTo("Olá!");
    }

    @Test
    @DisplayName("saveAssistantMessage deve persistir mensagem com role 'assistant'")
    void saveAssistantMessage_persistsCorrectRole() {
        service.saveAssistantMessage("sess-1", "Olá, posso ajudar!");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatHistoryPort).addMessage(eq("sess-1"), captor.capture());
        assertThat(captor.getValue().role()).isEqualTo("assistant");
    }

    @Test
    @DisplayName("listUserSessions delega ao port com limite 20")
    void listUserSessions_delegates() {
        when(chatHistoryPort.findSessionsByUser("alice", 20)).thenReturn(List.of(SESSION));

        List<ChatSession> result = service.listUserSessions("alice");

        assertThat(result).hasSize(1);
        verify(chatHistoryPort).findSessionsByUser("alice", 20);
    }

    @Test
    @DisplayName("getSessionWithMessages combina sessão e mensagens")
    void getSessionWithMessages_combinesData() {
        List<ChatMessage> msgs = List.of(new ChatMessage("user", "oi", Instant.now()));
        when(chatHistoryPort.findSession("sess-1")).thenReturn(Optional.of(SESSION));
        when(chatHistoryPort.loadMessages("sess-1")).thenReturn(msgs);

        Optional<ChatSession> result = service.getSessionWithMessages("sess-1");

        assertThat(result).isPresent();
        assertThat(result.get().messages()).hasSize(1);
        assertThat(result.get().messages().get(0).content()).isEqualTo("oi");
    }
}
