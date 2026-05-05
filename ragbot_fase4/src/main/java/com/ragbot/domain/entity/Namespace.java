package com.ragbot.domain.entity;

import java.time.Instant;

/**
 * Namespace — base de conhecimento isolada (#33).
 *
 * Cada namespace agrupa documentos e chunks independentes.
 * A busca vetorial é sempre escopada ao namespace ativo,
 * garantindo que perguntas de uma área não contaminem respostas de outra.
 *
 * Slug: identificador URL-friendly (ex: "rh", "financeiro", "default").
 */
public record Namespace(
        String id,
        String slug,
        String displayName,
        Instant createdAt
) {
    /** ID do namespace padrão — documentos antigos pertencem a ele. */
    public static final String DEFAULT_ID = "00000000-0000-0000-0000-000000000010";
    public static final String DEFAULT_SLUG = "default";
}
