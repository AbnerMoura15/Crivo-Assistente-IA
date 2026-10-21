package com.ragbot.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão de configuração do perfil prod.
 * Garante que segredos não têm valor padrão (o app falha no startup se faltarem)
 * e que o modo debug/exposição de detalhes não volta para produção.
 */
@DisplayName("application-prod.yml")
class ProdProfileConfigTest {

    private static Properties prod;

    @BeforeAll
    static void load() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        prod = yaml.getObject();
    }

    @Test
    @DisplayName("JWT secret vem só da variável JWT_SECRET, sem default")
    void jwtSecretHasNoDefault() {
        assertThat(prod.getProperty("ragbot.security.jwt.secret")).isEqualTo("${JWT_SECRET}");
    }

    @Test
    @DisplayName("senha do banco não tem default 'postgres'")
    void datasourcePasswordHasNoDefault() {
        assertThat(prod.getProperty("spring.datasource.password")).doesNotContain(":postgres");
        assertThat(prod.getProperty("spring.datasource.username")).doesNotContain(":postgres");
    }

    @Test
    @DisplayName("sem DEBUG, sem detalhes de health e sem stack trace em produção")
    void noDebugInProd() {
        assertThat(prod.getProperty("logging.level.com.ragbot")).isEqualTo("INFO");
        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(prod.getProperty("server.error.include-message")).isEqualTo("never");
    }
}
