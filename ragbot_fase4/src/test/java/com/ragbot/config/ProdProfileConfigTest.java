package com.ragbot.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
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
    @DisplayName("profile prod resolve ragbot.async.* (core=1, max=2, fila=10)")
    void asyncPoolResolvesInProdProfile() throws Exception {
        // Mesma precedência do Spring Boot: application-prod.yml sobrescreve application.yml.
        // Sem variáveis de ambiente, para validar os defaults declarados no arquivo.
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("prod", new ClassPathResource("application-prod.yml")).forEach(env.getPropertySources()::addLast);
        loader.load("base", new ClassPathResource("application.yml")).forEach(env.getPropertySources()::addLast);

        assertThat(env.getProperty("ragbot.async.core-pool-size", Integer.class)).isEqualTo(1);
        assertThat(env.getProperty("ragbot.async.max-pool-size", Integer.class)).isEqualTo(2);
        assertThat(env.getProperty("ragbot.async.queue-capacity", Integer.class)).isEqualTo(10);
        // regressão: as chaves não podem ter "vazado" para dentro de ragbot.security
        assertThat(prod.getProperty("ragbot.security.core-pool-size")).isNull();
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
