package com.ragbot.domain.entity;

/**
 * Configuração dinâmica do pipeline RAG (#25).
 *
 * Parâmetros ajustáveis pelo admin via tela, sem redeploy.
 * Persistidos na tabela rag_config (chave/valor) e carregados
 * com cache de 30 segundos pelo RagConfigService.
 */
public record RagConfig(
        int    topK,              // chunks retornados após re-ranking
        double minScore,          // score mínimo para aceitar um chunk
        int    shortChunkLength,  // chunks menores que isso recebem penalidade de 15%
        boolean hybridEnabled,    // liga busca híbrida vetorial + BM25
        double semanticWeight,    // peso vetorial na busca híbrida (0-1)
        long   monthlyTokenLimit  // limite de tokens estimados por usuário/mês
) {
    /** Valores padrão — usados quando o banco ainda não tem a tabela (testes sem DB). */
    public static RagConfig defaults() {
        return new RagConfig(3, 0.10, 80, true, 0.85, 100_000L);
    }
}
