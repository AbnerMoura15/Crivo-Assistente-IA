package com.ragbot.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração do Jackson.
 *
 * CORREÇÃO DO BUG: A versão anterior criava um ObjectMapper via "new ObjectMapper()"
 * substituindo completamente o configurado pelo Spring Boot — perdendo o
 * ParameterNamesModule, que é essencial para o Jackson serializar Java Records.
 * Sem ele, a JVM lança NoClassDefFoundError ao tentar resolver construtores de records.
 *
 * SOLUÇÃO: Usar Jackson2ObjectMapperBuilderCustomizer para ADICIONAR configurações
 * ao ObjectMapper auto-configurado pelo Spring Boot, sem substituí-lo.
 */
@Configuration
public class AppConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .modules(new JavaTimeModule())
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
