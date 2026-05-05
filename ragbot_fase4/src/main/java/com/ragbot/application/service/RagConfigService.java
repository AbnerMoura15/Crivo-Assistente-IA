package com.ragbot.application.service;

import com.ragbot.application.port.out.RagConfigPort;
import com.ragbot.domain.entity.RagConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Serviço de configuração dinâmica do RAG (#25).
 *
 * Mantém um cache em memória com TTL de 30 segundos para evitar
 * hits desnecessários ao banco em cada pergunta do chat.
 *
 * Quando o admin salva uma nova configuração via tela, o cache é
 * invalidado imediatamente (próxima leitura vai ao banco).
 */
@Service
public class RagConfigService {

    private static final Logger log = LoggerFactory.getLogger(RagConfigService.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final RagConfigPort configPort;

    private volatile RagConfig cachedConfig = null;
    private volatile Instant   cacheTime    = Instant.EPOCH;

    public RagConfigService(RagConfigPort configPort) {
        this.configPort = configPort;
    }

    /** Retorna a configuração atual, usando cache quando possível. */
    public RagConfig getConfig() {
        if (cachedConfig == null ||
                Duration.between(cacheTime, Instant.now()).compareTo(CACHE_TTL) > 0) {
            cachedConfig = configPort.load();
            cacheTime    = Instant.now();
            log.debug("RagConfig recarregado do banco: {}", cachedConfig);
        }
        return cachedConfig;
    }

    /**
     * Persiste a nova configuração e invalida o cache imediatamente.
     * A próxima chamada a getConfig() lerá os valores recém-salvos.
     */
    public void updateConfig(RagConfig config) {
        configPort.save(config);
        cachedConfig = config;
        cacheTime    = Instant.now();
        log.info("RagConfig atualizado pelo admin: {}", config);
    }

    /** Invalida o cache forçando releitura do banco na próxima chamada. */
    public void invalidateCache() {
        cachedConfig = null;
    }
}
