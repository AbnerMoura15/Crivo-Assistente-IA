package com.ragbot.adapter.in.rest;

import com.ragbot.application.service.AuditLogService;
import com.ragbot.application.service.ChatHistoryService;
import com.ragbot.application.service.RateLimiterService;
import com.ragbot.config.security.JwtTokenProvider;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuração para testes que NÃO precisam de autenticação (ex: AuthControllerTest).
 * - Desabilita toda a segurança (permitAll).
 * - Provê mocks dos beans exigidos pelos filtros e controllers.
 */
@TestConfiguration
public class TestSecurityConfig {

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

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
