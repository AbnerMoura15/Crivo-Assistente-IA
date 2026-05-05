package com.ragbot.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Serviço #31 — Mascaramento de PII (Personally Identifiable Information).
 *
 * LGPD (Lei Geral de Proteção de Dados) exige que dados pessoais sensíveis
 * não sejam transmitidos para terceiros sem consentimento explícito.
 *
 * Este serviço substitui dados identificáveis por tokens antes de enviar
 * o contexto dos documentos para APIs externas (Groq, Google).
 *
 * IMPORTANTE:
 * - O banco de dados mantém o texto ORIGINAL (necessário para busca semântica)
 * - Apenas o payload enviado à IA é mascarado
 * - As respostas da IA podem conter os tokens [CPF], [EMAIL], etc.
 *
 * Padrões cobertos (Brasil):
 * - CPF: 000.000.000-00 ou 00000000000
 * - CNPJ: 00.000.000/0000-00
 * - Email: padrão RFC 5322 simplificado
 * - Telefone: (DDD) 9XXXX-XXXX ou (DDD) XXXX-XXXX
 * - Valor monetário: R$ X.XXX,XX
 */
@Service
public class PiiMaskingService {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingService.class);

    // CPF: 000.000.000-00 ou 00000000000
    private static final Pattern CPF = Pattern.compile(
            "\\b(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11})\\b");

    // CNPJ: 00.000.000/0000-00
    private static final Pattern CNPJ = Pattern.compile(
            "\\b\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}\\b");

    // Email
    private static final Pattern EMAIL = Pattern.compile(
            "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

    // Telefone brasileiro: (11) 99999-8888 ou (11) 3333-4444
    private static final Pattern PHONE = Pattern.compile(
            "\\(\\d{2}\\)\\s?9?\\d{4}[\\-\\s]?\\d{4}");

    // Valor monetário: R$ 1.000,00 ou R$1000,00
    private static final Pattern MONEY = Pattern.compile(
            "R\\$\\s?\\d{1,3}(\\.\\d{3})*(,\\d{2})?");

    /**
     * Mascara todos os padrões de PII encontrados no texto.
     *
     * @param text texto bruto que pode conter PII
     * @return texto com PII substituída por tokens como [CPF], [EMAIL], etc.
     */
    public String mask(String text) {
        if (text == null || text.isBlank()) return "";

        String result = text;
        result = CNPJ.matcher(result).replaceAll("[CNPJ]");   // CNPJ antes de CPF (mais específico)
        result = CPF.matcher(result).replaceAll("[CPF]");
        result = EMAIL.matcher(result).replaceAll("[EMAIL]");
        result = PHONE.matcher(result).replaceAll("[TELEFONE]");
        result = MONEY.matcher(result).replaceAll("[VALOR]");

        return result;
    }

    /**
     * Mascara o texto e retorna um resultado com estatísticas.
     * Útil para audit log e métricas de compliance.
     */
    public MaskResult maskWithStats(String text) {
        if (text == null || text.isBlank()) return new MaskResult("", 0);

        AtomicInteger count = new AtomicInteger(0);

        String result = text;
        result = replaceAndCount(CNPJ,  result, "[CNPJ]",    count);
        result = replaceAndCount(CPF,   result, "[CPF]",     count);
        result = replaceAndCount(EMAIL, result, "[EMAIL]",   count);
        result = replaceAndCount(PHONE, result, "[TELEFONE]",count);
        result = replaceAndCount(MONEY, result, "[VALOR]",   count);

        int total = count.get();
        if (total > 0) {
            log.info("PII mascarada: {} substituicoes realizadas", total);
        }

        return new MaskResult(result, total);
    }

    private String replaceAndCount(Pattern pattern, String text, String replacement, AtomicInteger counter) {
        return pattern.matcher(text).replaceAll(match -> {
            counter.incrementAndGet();
            return replacement;
        });
    }

    /**
     * Resultado do mascaramento com contagem de substituições.
     */
    public record MaskResult(String maskedText, int replacementsCount) {}
}
