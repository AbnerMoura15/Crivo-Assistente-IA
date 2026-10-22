package com.ragbot.application.service;

import com.ragbot.application.port.in.ManageNamespacesUseCase;
import com.ragbot.application.port.out.DocumentStoragePort;
import com.ragbot.domain.entity.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serviço de gerenciamento de namespaces (#33).
 */
@Service
public class NamespaceService implements ManageNamespacesUseCase {

    private static final Logger log = LoggerFactory.getLogger(NamespaceService.class);

    private final DocumentStoragePort storagePort;

    public NamespaceService(DocumentStoragePort storagePort) {
        this.storagePort = storagePort;
    }

    @Override
    public List<Namespace> listAll() {
        return storagePort.findAllNamespaces();
    }

    @Override
    public Namespace create(String slug, String displayName) {
        // Valida slug: apenas letras minúsculas, números e hífen
        if (!slug.matches("[a-z0-9-]+")) {
            throw new IllegalArgumentException(
                    "Slug inválido: use apenas letras minúsculas, números e hífen.");
        }
        if (storagePort.findNamespaceBySlug(slug).isPresent()) {
            throw new IllegalArgumentException("Já existe um namespace com o slug: " + slug);
        }
        Namespace ns = storagePort.createNamespace(slug, displayName);
        log.info("Namespace criado: slug={} displayName={}", slug, displayName);
        return ns;
    }

    @Override
    public void delete(String namespaceId) {
        storagePort.deleteNamespace(namespaceId);
        log.info("Namespace removido: {}", namespaceId);
    }
}
