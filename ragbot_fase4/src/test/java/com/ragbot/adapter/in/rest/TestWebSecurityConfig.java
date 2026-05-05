package com.ragbot.adapter.in.rest;

import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.ChatHistoryService;
import com.ragbot.application.service.RateLimiterService;
import com.ragbot.config.security.JwtTokenProvider;
import com.ragbot.config.security.SecurityConfig;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * Configuração para testes que PRECISAM das regras de segurança reais
 * (ex: SecurityEndpointsTest, FeedbackControllerTest).
 *
 * Importa o SecurityConfig real + provê mocks dos beans exigidos pelos filtros.
 */
@TestConfiguration
@Import(SecurityConfig.class)
public class TestWebSecurityConfig {

    @Bean
    public RateLimiterService rateLimiterService() {
        RateLimiterService mock = Mockito.mock(RateLimiterService.class);
        Mockito.when(mock.tryAcquire(Mockito.anyString())).thenReturn(true);
        return mock;
    }

    @Bean
    public JwtTokenProvider jwtTokenProvider() {
        return Mockito.mock(JwtTokenProvider.class);
    }

    @Bean
    public ChatHistoryService chatHistoryService() {
        return Mockito.mock(ChatHistoryService.class);
    }

    @Bean
    public AuditLogService auditLogService() {
        return Mockito.mock(AuditLogService.class);
    }
}
