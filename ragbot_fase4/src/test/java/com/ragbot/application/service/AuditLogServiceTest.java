package com.ragbot.application.service;

import com.ragbot.application.port.out.AuditLogPort;
import com.ragbot.domain.entity.AuditAction;
import com.ragbot.domain.entity.AuditEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditLogService")
class AuditLogServiceTest {

    @Mock AuditLogPort auditLogPort;

    AuditLogService service;

    @BeforeEach
    void setUp() {
        service = new AuditLogService(auditLogPort);
    }

    @Test
    @DisplayName("record completo deve persistir todos os campos")
    void recordShouldPersistAllFields() {
        service.record("alice", AuditAction.DOCUMENT_UPLOAD, "doc-1", "DOCUMENT",
                "arquivo=manual.pdf", "192.168.1.1");

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogPort).save(captor.capture());

        AuditEntry saved = captor.getValue();
        assertThat(saved.actor()).isEqualTo("alice");
        assertThat(saved.action()).isEqualTo(AuditAction.DOCUMENT_UPLOAD);
        assertThat(saved.targetId()).isEqualTo("doc-1");
        assertThat(saved.targetType()).isEqualTo("DOCUMENT");
        assertThat(saved.detail()).isEqualTo("arquivo=manual.pdf");
        assertThat(saved.ipAddress()).isEqualTo("192.168.1.1");
        assertThat(saved.id()).isNotBlank();
        assertThat(saved.createdAt()).isNotNull();
    }

    @Test
    @DisplayName("record simplificado deve usar actor 'system' quando null")
    void recordSimplifiedShouldDefaultActor() {
        service.record(null, AuditAction.REGISTER, "detalhe qualquer");

        ArgumentCaptor<AuditEntry> captor = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditLogPort).save(captor.capture());

        assertThat(captor.getValue().actor()).isEqualTo("system");
        assertThat(captor.getValue().action()).isEqualTo(AuditAction.REGISTER);
    }

    @Test
    @DisplayName("exceção no adapter não deve propagar — nunca derruba o fluxo principal")
    void recordShouldNotPropagateAdapterException() {
        doThrow(new RuntimeException("DB offline")).when(auditLogPort).save(any());

        // deve completar sem lançar exceção
        service.record("bob", AuditAction.LOGIN_SUCCESS, "ok");
    }

    @Test
    @DisplayName("findRecent delega ao port com o limite correto")
    void findRecentShouldDelegate() {
        AuditEntry entry = new AuditEntry("id", "alice", AuditAction.LOGIN_SUCCESS,
                null, null, null, null, Instant.now());
        when(auditLogPort.findRecent(10)).thenReturn(List.of(entry));

        List<AuditEntry> result = service.findRecent(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).actor()).isEqualTo("alice");
        verify(auditLogPort).findRecent(10);
    }

    @Test
    @DisplayName("findByActor filtra por actor no port")
    void findByActorShouldDelegate() {
        when(auditLogPort.findByActor("alice", 5)).thenReturn(List.of());
        service.findByActor("alice", 5);
        verify(auditLogPort).findByActor("alice", 5);
    }

    @Test
    @DisplayName("findByAction filtra por ação no port")
    void findByActionShouldDelegate() {
        when(auditLogPort.findByAction(AuditAction.DOCUMENT_DELETE, 20)).thenReturn(List.of());
        service.findByAction(AuditAction.DOCUMENT_DELETE, 20);
        verify(auditLogPort).findByAction(AuditAction.DOCUMENT_DELETE, 20);
    }
}
