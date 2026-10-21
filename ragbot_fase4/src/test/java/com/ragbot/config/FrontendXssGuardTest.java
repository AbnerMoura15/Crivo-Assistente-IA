package com.ragbot.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressão estática do front (não há runner JS no projeto).
 * Garante que dados controlados por usuário (nome de arquivo, namespace) não voltem
 * a ser interpolados sem escape em atributos HTML ou em strings JS de onclick.
 */
@DisplayName("index.html — proteção contra XSS")
class FrontendXssGuardTest {

    private static String html() throws IOException {
        return new ClassPathResource("static/index.html").getContentAsString(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("escHtml escapa aspas simples e duplas")
    void escHtmlEscapesQuotes() throws IOException {
        assertThat(html()).contains(".replace(/\"/g,'&quot;').replace(/'/g,'&#39;')");
    }

    @Test
    @DisplayName("nenhum nome de arquivo/namespace vai cru para atributo ou string JS")
    void noRawUserDataInAttributes() throws IOException {
        String html = html();
        assertThat(html).doesNotContain("title=\"${doc.fileName}\"");
        assertThat(html).doesNotContain("'${escHtml(doc.fileName)}'");
        assertThat(html).doesNotContain("'${escHtml(ns.displayName)}'");
        assertThat(html).contains("${jsArg(doc.fileName)}");
    }
}
