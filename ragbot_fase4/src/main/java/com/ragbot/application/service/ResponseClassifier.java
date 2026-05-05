package com.ragbot.application.service;

import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serviço #8 — Classificação automática de respostas do RAG.
 *
 * Analisa o texto da resposta e determina se o modelo conseguiu
 * responder ou declarou que não encontrou a informação.
 *
 * Essa classificação é usada em:
 * - RagInteraction para registro histórico
 * - Dashboard de qualidade (taxa de NOT_FOUND é KPI principal)
 * - Alertas quando NOT_FOUND ultrapassa threshold
 */
@Service
public class ResponseClassifier {

    public enum Classification {
        ANSWERED,    // modelo respondeu com conteúdo dos documentos
        NOT_FOUND    // modelo declarou que não encontrou a informação
    }

    // Frases que indicam que o modelo não encontrou a informação
    private static final List<String> NOT_FOUND_PATTERNS = List.of(
            "não encontrei",
            "nao encontrei",
            "não foi encontrado",
            "nao foi encontrado",
            "nenhum documento",
            "não há informação",
            "nao ha informacao",
            "não tenho informação",
            "não está nos documentos",
            "faça o upload"
    );

    public Classification classify(String response) {
        if (response == null || response.isBlank()) {
            return Classification.NOT_FOUND;
        }

        String lower = response.toLowerCase();
        boolean isNotFound = NOT_FOUND_PATTERNS.stream()
                .anyMatch(lower::contains);

        return isNotFound ? Classification.NOT_FOUND : Classification.ANSWERED;
    }
}
