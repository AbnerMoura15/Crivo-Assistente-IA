package com.ragbot.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD #31 — PiiMaskingService.
 *
 * Contrato: mascarar dados sensíveis antes de enviar para APIs externas (Groq/Google).
 * O banco mantém os dados originais — apenas o payload enviado para fora é mascarado.
 *
 * Padrões brasileiros cobertos: CPF, CNPJ, RG, email, telefone, valor monetário.
 */
@DisplayName("PiiMaskingService")
class PiiMaskingServiceTest {

    private PiiMaskingService service;

    @BeforeEach
    void setUp() {
        service = new PiiMaskingService();
    }

    // ── CPF ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve mascarar CPF com pontuação")
    void shouldMaskCpfWithPunctuation() {
        String result = service.mask("O funcionário 123.456.789-00 foi admitido.");
        assertThat(result)
                .contains("[CPF]")
                .doesNotContain("123.456.789-00");
    }

    @Test
    @DisplayName("deve mascarar CPF sem pontuação")
    void shouldMaskCpfWithoutPunctuation() {
        String result = service.mask("CPF do colaborador: 12345678900");
        assertThat(result)
                .contains("[CPF]")
                .doesNotContain("12345678900");
    }

    @Test
    @DisplayName("deve mascarar múltiplos CPFs no mesmo texto")
    void shouldMaskMultipleCpfs() {
        String result = service.mask("João: 123.456.789-00 e Maria: 987.654.321-00");
        assertThat(result)
                .doesNotContain("123.456.789-00")
                .doesNotContain("987.654.321-00");
        // split(-1) preserva segmentos vazios no final — evita descarte padrão do Java
        assertThat(result.split("\\[CPF\\]", -1).length - 1).isEqualTo(2);
    }

    // ── CNPJ ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve mascarar CNPJ com pontuação")
    void shouldMaskCnpj() {
        String result = service.mask("Empresa CNPJ 12.345.678/0001-99 contratada.");
        assertThat(result)
                .contains("[CNPJ]")
                .doesNotContain("12.345.678/0001-99");
    }

    // ── Email ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve mascarar endereços de email")
    void shouldMaskEmail() {
        String result = service.mask("Contato: joao.silva@empresa.com.br para mais detalhes.");
        assertThat(result)
                .contains("[EMAIL]")
                .doesNotContain("joao.silva@empresa.com.br");
    }

    // ── Telefone ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve mascarar telefone celular com DDD")
    void shouldMaskCellPhone() {
        String result = service.mask("Ligar para (11) 99999-8888 em caso de urgência.");
        assertThat(result)
                .contains("[TELEFONE]")
                .doesNotContain("99999-8888");
    }

    @Test
    @DisplayName("deve mascarar telefone fixo")
    void shouldMaskLandline() {
        String result = service.mask("Central: (21) 3333-4444.");
        assertThat(result)
                .contains("[TELEFONE]")
                .doesNotContain("3333-4444");
    }

    // ── Valor monetário ───────────────────────────────────────────────────────

    @Test
    @DisplayName("deve mascarar valores monetários em reais")
    void shouldMaskMonetaryValues() {
        String result = service.mask("Salário: R$ 8.500,00 por mês.");
        assertThat(result)
                .contains("[VALOR]")
                .doesNotContain("8.500,00");
    }

    @Test
    @DisplayName("deve mascarar valor sem espaço após R$")
    void shouldMaskMonetaryValueWithoutSpace() {
        String result = service.mask("Total: R$1.200,50");
        assertThat(result).contains("[VALOR]");
    }

    // ── Texto limpo ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("texto sem PII não deve ser alterado")
    void textWithoutPiiShouldBeUnchanged() {
        String clean = "O procedimento de manutenção deve seguir a norma ISO 9001.";
        assertThat(service.mask(clean)).isEqualTo(clean);
    }

    @Test
    @DisplayName("texto nulo deve retornar string vazia")
    void nullTextShouldReturnEmpty() {
        assertThat(service.mask(null)).isEmpty();
    }

    @Test
    @DisplayName("texto vazio deve retornar string vazia")
    void emptyTextShouldReturnEmpty() {
        assertThat(service.mask("")).isEmpty();
    }

    // ── Combinações ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("deve mascarar múltiplos tipos de PII no mesmo texto")
    void shouldMaskMultiplePiiTypes() {
        String text = """
                Registro de funcionário:
                Nome: João da Silva
                CPF: 123.456.789-00
                Email: joao@fabrica.com
                Salário: R$ 5.200,00
                Telefone: (11) 98765-4321
                """;

        String result = service.mask(text);

        assertThat(result)
                .contains("[CPF]")
                .contains("[EMAIL]")
                .contains("[VALOR]")
                .contains("[TELEFONE]")
                .doesNotContain("123.456.789-00")
                .doesNotContain("joao@fabrica.com")
                .doesNotContain("5.200,00")
                .doesNotContain("98765-4321")
                // Nome não é PII detectável por regex simples — mantido
                .contains("João da Silva");
    }

    @Test
    @DisplayName("deve retornar contagem de substituições realizadas")
    void shouldReturnMaskingStats() {
        String text = "CPF: 123.456.789-00 e email: teste@fab.com";
        PiiMaskingService.MaskResult result = service.maskWithStats(text);

        assertThat(result.maskedText()).contains("[CPF]").contains("[EMAIL]");
        assertThat(result.replacementsCount()).isEqualTo(2);
    }
}
