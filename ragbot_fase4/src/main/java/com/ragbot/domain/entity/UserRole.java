package com.ragbot.domain.entity;

/**
 * Perfis de acesso do RAGBot.
 *
 * VIEWER → apenas consultar documentos e usar o chat
 * EDITOR → VIEWER + fazer upload de documentos
 * ADMIN  → EDITOR + deletar documentos + gerenciar usuários
 */
public enum UserRole {
    VIEWER,
    EDITOR,
    ADMIN
}
