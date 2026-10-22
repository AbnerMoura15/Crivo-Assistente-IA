package com.ragbot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuração do pool de threads para ingestão assíncrona (#22).
 *
 * Parâmetros configuráveis via application.yml:
 *   ragbot.async.core-pool-size   (padrão: 2)
 *   ragbot.async.max-pool-size    (padrão: 5)
 *   ragbot.async.queue-capacity   (padrão: 50)
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "ingestExecutor")
    public Executor ingestExecutor(
            @Value("${ragbot.async.core-pool-size:2}") int corePoolSize,
            @Value("${ragbot.async.max-pool-size:5}") int maxPoolSize,
            @Value("${ragbot.async.queue-capacity:50}") int queueCapacity) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
