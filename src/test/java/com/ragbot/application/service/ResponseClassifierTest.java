package com.ragbot.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TDD #8 — ResponseClassifier.
 *
 * Classifica automaticamente a qualidade de cada resposta do RAG:
 *   NOT_FOUND      → modelo disse que não encontrou a informação
 *   ANSWERED       → resposta normal com conteúdo
 */
@DisplayName("ResponseClassifier")
class ResponseClassifierTest {

    private ResponseClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ResponseClassifier();
    }

    @Test
    @DisplayName("resposta com frase padrão de não encontrado → NOT_FOUND")
    void notFoundPhraseShouldClassifyAsNotFound() {
        String response = "Não encontrei essa informação nos documentos indexados.";
        assertThat(classifier.classify(response)).isEqualTo(ResponseClassifier.Classification.NOT_FOUND);
    }

    @Test
    @DisplayName("variação da frase padrão → NOT_FOUND")
    void variationOfNotFoundShouldClassify() {
        String response = "Não encontrei informações sobre esse procedimento nos documentos.";
        assertThat(classifier.classify(response)).isEqualTo(ResponseClassifier.Classification.NOT_FOUND);
    }

    @Test
    @DisplayName("nenhum documento encontrado → NOT_FOUND")
    void noDocumentFoundShouldClassify() {
        String response = "Nenhum documento foi encontrado para responder sua pergunta.";
        assertThat(classifier.classify(response)).isEqualTo(ResponseClassifier.Classification.NOT_FOUND);
    }

    @Test
    @DisplayName("resposta com conteúdo → ANSWERED")
    void contentfulResponseShouldBeAnswered() {
        String response = "O procedimento de segurança descrito no manual é: utilizar EPI completo.";
        assertThat(classifier.classify(response)).isEqualTo(ResponseClassifier.Classification.ANSWERED);
    }

    @Test
    @DisplayName("resposta nula → NOT_FOUND")
    void nullResponseShouldBeNotFound() {
        assertThat(classifier.classify(null)).isEqualTo(ResponseClassifier.Classification.NOT_FOUND);
    }

    @Test
    @DisplayName("resposta vazia → NOT_FOUND")
    void emptyResponseShouldBeNotFound() {
        assertThat(classifier.classify("")).isEqualTo(ResponseClassifier.Classification.NOT_FOUND);
    }

    @Test
    @DisplayName("resposta longa com conteúdo → ANSWERED")
    void longAnswerShouldBeAnswered() {
        String response = "De acordo com o manual de segurança (Seção 3.2), o procedimento para "
                + "manuseio de substâncias químicas inclui: 1) Usar luvas nitrílicas; "
                + "2) Manter ventilação adequada; 3) Armazenar em local seco e fresco.";
        assertThat(classifier.classify(response)).isEqualTo(ResponseClassifier.Classification.ANSWERED);
    }
}
