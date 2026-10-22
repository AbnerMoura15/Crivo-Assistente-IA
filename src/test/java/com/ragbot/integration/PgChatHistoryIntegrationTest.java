package com.ragbot.integration;

import com.ragbot.adapter.out.chat.PgChatHistoryAdapter;
import com.ragbot.domain.entity.ChatMessage;
import com.ragbot.domain.entity.ChatSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes de integração para PgChatHistoryAdapter.
 *
 * Valida criação de sessões, adição de mensagens, listagem, busca por usuário
 * e remoção de sessão contra PostgreSQL real (Testcontainers).
 */
class PgChatHistoryIntegrationTest extends AbstractIntegrationTest {

    private PgChatHistoryAdapter adapter;

    @BeforeEach
    void criarAdapter() {
        adapter = new PgChatHistoryAdapter(JDBC);
    }

    // ── createSession ────────────────────────────────────────────────────────

    @Test
    void createSession_retornaSessionComId() {
        ChatSession session = adapter.createSession("joao");

        assertThat(session.id()).isNotBlank();
        assertThat(session.username()).isEqualTo("joao");
        assertThat(session.createdAt()).isNotNull();
        assertThat(session.lastMessageAt()).isNotNull();
    }

    @Test
    void createSession_persisteNoBanco() {
        ChatSession session = adapter.createSession("maria");

        Integer count = JDBC.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE id = ?",
                Integer.class, session.id());
        assertThat(count).isEqualTo(1);
    }

    // ── findSession ──────────────────────────────────────────────────────────

    @Test
    void findSession_retornaSessionCriada() {
        ChatSession criada = adapter.createSession("pedro");

        Optional<ChatSession> encontrada = adapter.findSession(criada.id());

        assertThat(encontrada).isPresent();
        assertThat(encontrada.get().id()).isEqualTo(criada.id());
        assertThat(encontrada.get().username()).isEqualTo("pedro");
    }

    @Test
    void findSession_retornaVazioParaIdInexistente() {
        Optional<ChatSession> resultado = adapter.findSession("session-inexistente");
        assertThat(resultado).isEmpty();
    }

    // ── addMessage + loadMessages ────────────────────────────────────────────

    @Test
    void addMessage_persisteMensagemDeTipoUser() {
        ChatSession session = adapter.createSession("ana");
        adapter.addMessage(session.id(), ChatMessage.user("Qual é o prazo?"));

        List<ChatMessage> msgs = adapter.loadMessages(session.id());
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).role()).isEqualTo("user");
        assertThat(msgs.get(0).content()).isEqualTo("Qual é o prazo?");
    }

    @Test
    void addMessage_persisteMensagemDeTipoAssistant() {
        ChatSession session = adapter.createSession("ana");
        adapter.addMessage(session.id(), ChatMessage.assistant("O prazo é 30 dias."));

        List<ChatMessage> msgs = adapter.loadMessages(session.id());
        assertThat(msgs).hasSize(1);
        assertThat(msgs.get(0).role()).isEqualTo("assistant");
    }

    @Test
    void loadMessages_retornaMensagensEmOrdemCronologica() throws InterruptedException {
        ChatSession session = adapter.createSession("carlos");
        adapter.addMessage(session.id(), ChatMessage.user("Primeira pergunta"));
        // Pequena pausa para garantir ordering por created_at
        Thread.sleep(10);
        adapter.addMessage(session.id(), ChatMessage.assistant("Primeira resposta"));
        Thread.sleep(10);
        adapter.addMessage(session.id(), ChatMessage.user("Segunda pergunta"));

        List<ChatMessage> msgs = adapter.loadMessages(session.id());

        assertThat(msgs).hasSize(3);
        assertThat(msgs.get(0).role()).isEqualTo("user");
        assertThat(msgs.get(0).content()).contains("Primeira");
        assertThat(msgs.get(2).role()).isEqualTo("user");
        assertThat(msgs.get(2).content()).contains("Segunda");
    }

    @Test
    void loadMessages_retornaVazioParaSessionSemMensagens() {
        ChatSession session = adapter.createSession("vazio");
        List<ChatMessage> msgs = adapter.loadMessages(session.id());
        assertThat(msgs).isEmpty();
    }

    @Test
    void addMessage_atualizaLastMessageAt() throws InterruptedException {
        ChatSession session = adapter.createSession("lucas");
        java.time.Instant antes = session.lastMessageAt();

        Thread.sleep(20);
        adapter.addMessage(session.id(), ChatMessage.user("Olá"));

        ChatSession atualizada = adapter.findSession(session.id()).orElseThrow();
        assertThat(atualizada.lastMessageAt()).isAfterOrEqualTo(antes);
    }

    // ── findSessionsByUser ───────────────────────────────────────────────────

    @Test
    void findSessionsByUser_retornaSessionsDoUsuario() {
        adapter.createSession("alice");
        adapter.createSession("alice");
        adapter.createSession("bob");

        List<ChatSession> aliceSessions = adapter.findSessionsByUser("alice", 10);
        assertThat(aliceSessions).hasSize(2);
        assertThat(aliceSessions).allMatch(s -> "alice".equals(s.username()));
    }

    @Test
    void findSessionsByUser_respeitaLimite() {
        for (int i = 0; i < 5; i++) {
            adapter.createSession("testuser");
        }

        List<ChatSession> sessions = adapter.findSessionsByUser("testuser", 3);
        assertThat(sessions).hasSize(3);
    }

    @Test
    void findSessionsByUser_retornaVazioParaUsuarioSemSessions() {
        List<ChatSession> sessions = adapter.findSessionsByUser("ninguem", 10);
        assertThat(sessions).isEmpty();
    }

    // ── deleteSession ────────────────────────────────────────────────────────

    @Test
    void deleteSession_removeSessionDoBanco() {
        ChatSession session = adapter.createSession("delete-test");
        adapter.addMessage(session.id(), ChatMessage.user("Mensagem que será deletada"));

        adapter.deleteSession(session.id());

        Integer countSession = JDBC.queryForObject(
                "SELECT COUNT(*) FROM chat_sessions WHERE id = ?",
                Integer.class, session.id());
        assertThat(countSession).isZero();
    }

    @Test
    void deleteSession_removeMensagensAssociadas() {
        ChatSession session = adapter.createSession("delete-msgs");
        adapter.addMessage(session.id(), ChatMessage.user("Pergunta 1"));
        adapter.addMessage(session.id(), ChatMessage.assistant("Resposta 1"));

        adapter.deleteSession(session.id());

        Integer countMsgs = JDBC.queryForObject(
                "SELECT COUNT(*) FROM chat_messages WHERE session_id = ?",
                Integer.class, session.id());
        assertThat(countMsgs).isZero();
    }

    @Test
    void findSession_retornaVazioAposDelete() {
        ChatSession session = adapter.createSession("pos-delete");
        adapter.deleteSession(session.id());

        Optional<ChatSession> resultado = adapter.findSession(session.id());
        assertThat(resultado).isEmpty();
    }
}
