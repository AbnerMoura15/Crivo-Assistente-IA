# RAGBot — O que ainda falta

> Atualizado em: 2026-05-05 | Estado atual: **Fase 8 concluída** | Testes: 115 ✅

---

## ✅ Fases concluídas

| Fase | Itens entregues |
|---|---|
| **1–6** | Autenticação JWT, Rate Limit, PII Masking, Soft Delete, Observabilidade, Audit Log, Chat History, Feedback, Infra Docker |
| **7** | Frontend completo, segurança 401/403 corrigida, testes estabilizados |
| **8** | Busca híbrida (#27), re-ranking heurístico (#28a), controle de tokens (#4) |

---

## 🔴 Dívidas do frontend (backend pronto, sem UI)

| Feature | Backend | Frontend |
|---|---|---|
| **Chat History** (#13) | ✅ banco persiste sessões + `sessionId` no response | ❌ frontend usa histórico em memória; ignora `sessionId` |
| **Audit Log** (#11) | ✅ `GET /api/admin/audit-log` | ❌ sem view |
| **Sessões de chat** (#13) | ✅ `GET /api/chat/sessions` | ❌ sem view de histórico |
| **Dashboard RAG** (#10) | ✅ dados em `rag_interactions` + `feedback` | ❌ sem view de métricas |

---

## 🟡 Roadmap pendente (por prioridade)

### Fase 9 — Confiabilidade e testes
| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#15** | Testes unitários (JaCoCo ≥80%) | 🟡 | Cobrir adapters e controllers; relatório HTML |
| **#16** | Testes de integração (Testcontainers) | 🔴 | PostgreSQL + pgvector real nos testes; testar Flyway, queries vetoriais |

### Fase 10 — Features avançadas
| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#22** | Ingestão assíncrona | 🟡 | Upload retorna imediato; chunking/embedding em background thread |
| **#25** | Config IA via tela | 🟡 | Admin altera provider, top-k, chunk-size sem reiniciar o servidor |
| **#26** | Versionamento de documentos | 🔴 | Re-upload cria nova versão; chunks antigos ficam vinculados à versão anterior |
| **#33** | Namespaces/coleções | 🟡 | Separar documentos por departamento/projeto |
| **#30** | Multi-tenancy | 🔴 | Isolamento completo por tenant |

### Frontend — fechar dívidas abertas
| Item | Arquivo | Prioridade |
|---|---|---|
| Chat usar `sessionId` do banco | `index.html` | 🔴 |
| Painel "Histórico de Conversas" | `index.html` | 🟡 |
| Painel "Auditoria" (ADMIN) | `index.html` | 🟡 |
| Dashboard qualidade RAG (#10) | `index.html` | 🟡 |

---

## 🗺️ Resumo visual

```
✅ Implementado    ⏳ Próxima fase    🔲 Backlog

[✅ Autenticação JWT]  [✅ Rate Limit]    [✅ PII Mask]
[✅ Soft Delete]       [✅ Observab.]     [✅ Audit Log]
[✅ Chat History DB]   [✅ Feedback]      [✅ Infra Docker]
[✅ Busca Híbrida]     [✅ Re-ranking]    [✅ Token Control]
[✅ Frontend base]

[⏳ sessionId no chat]  [⏳ Histórico UI]  [⏳ Audit UI]
[⏳ Dashboard RAG]      [⏳ Testcontainers]

[🔲 Async Ingest]  [🔲 Config via UI]  [🔲 Versionamento]
[🔲 Namespaces]    [🔲 Multi-tenancy]
```

---

## ⚡ Sugestão para próxima sessão

**Fase 9 — Testcontainers** é o mais indicado porque:
1. Testa as queries vetoriais reais (pgvector) que são difíceis de mockar
2. Valida as 10 migrations Flyway de ponta a ponta
3. Aumenta confiança para mudanças futuras no schema e nas queries SQL

**Alternativa menor:** fechar as dívidas do frontend (sessionId + histórico) — impacto imediato para o usuário final, sem risco de regressão.
