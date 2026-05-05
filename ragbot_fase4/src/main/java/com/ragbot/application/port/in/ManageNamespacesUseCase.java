package com.ragbot.application.port.in;

import com.ragbot.domain.entity.Namespace;

import java.util.List;

/**
 * Caso de uso para gerenciamento de namespaces (#33).
 * Apenas ADMINs podem criar e deletar namespaces.
 */
public interface ManageNamespacesUseCase {
    List<Namespace> listAll();
    Namespace create(String slug, String displayName);
    void delete(String namespaceId);
}
