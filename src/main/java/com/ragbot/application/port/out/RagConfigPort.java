package com.ragbot.application.port.out;

import com.ragbot.domain.entity.RagConfig;

public interface RagConfigPort {
    /** Lê todas as chaves da tabela rag_config e monta o RagConfig. */
    RagConfig load();

    /** Persiste todos os campos do RagConfig na tabela rag_config. */
    void save(RagConfig config);
}
