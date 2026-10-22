package com.ragbot.config.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ragbot.application.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Filtro #5 — Rate Limiting.
 *
 * Aplica limites diferentes por perfil:
 * - Usuário autenticado: chave "user:<username>" → limite maior
 * - Requisição anônima: chave "ip:<ip>"         → limite menor
 *
 * Health/actuator e estáticos são isentos. O login conta no limite anônimo por IP.
 * Retorna HTTP 429 com corpo JSON quando o limite é atingido.
 */
@Component
@Order(2) // Roda após RequestIdFilter (Order 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        // Endpoints isentos de rate limiting
        String path = request.getRequestURI();
        if (isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        String key = resolveKey(request);

        if (!rateLimiterService.tryAcquire(key)) {
            log.warn("Rate limit atingido: key={} uri={}", key, path);
            sendTooManyRequests(response, key);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Determina a chave de rate limiting:
     * - Usuário autenticado → "user:<username>"
     * - Anônimo → "ip:<ip>"
     */
    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();
        }
        return "ip:" + extractClientIp(request);
    }

    /**
     * NÃO lê X-Forwarded-For diretamente: o cliente poderia forjá-lo para escapar do limite.
     * Atrás de proxy, server.forward-headers-strategy=native faz o Tomcat preencher
     * getRemoteAddr() com o IP real somente quando o header vem de um proxy confiável.
     */
    private String extractClientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    /** /api/auth/login NÃO é isento: o limite anônimo por IP freia força bruta de senha. */
    private boolean isExempt(String path) {
        return path.startsWith("/actuator/")
                || path.equals("/")
                || path.endsWith(".html")
                || path.endsWith(".css")
                || path.endsWith(".js");
    }

    private void sendTooManyRequests(HttpServletResponse response, String key) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(Map.of(
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Limite de requisições atingido. Aguarde um momento e tente novamente.",
                "key", key.startsWith("user:") ? key : "anônimo"
        )));
    }
}
