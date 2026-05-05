package com.ragbot.config.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Filtro #12 — Request ID + Rastreabilidade.
 *
 * Para cada requisição:
 * 1. Lê o header X-Request-ID do cliente (ou gera um UUID novo)
 * 2. Injeta no MDC do SLF4J para aparecer em TODOS os logs daquela requisição
 * 3. Adiciona X-Request-ID na resposta HTTP para o cliente rastrear
 *
 * Com isso, qualquer log de erro em produção pode ser correlacionado
 * com a requisição exata que o causou — essencial para suporte e debugging.
 *
 * Exemplo de log gerado:
 * {"timestamp":"...","level":"ERROR","requestId":"abc-123","message":"Falha ao..."}
 */
@Component
@Order(1) // Deve rodar ANTES de qualquer outro filtro
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String requestId = request.getHeader(REQUEST_ID_HEADER);

        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // Injeta no MDC — aparece automaticamente em todos os logs desta thread
        MDC.put(MDC_KEY, requestId);
        // Retorna ao cliente para ele poder referenciar em suporte
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            chain.doFilter(request, response);
        } finally {
            // Obrigatório limpar MDC para não vazar entre requisições (thread pool)
            MDC.remove(MDC_KEY);
        }
    }
}
