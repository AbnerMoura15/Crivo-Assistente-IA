# RAGBot — Resumo da Sessão (Fases 7 + 8)

> Data: 2026-05-05 | Testes: **115 passando, 0 falhas**

---

## ✅ O que foi entregue

### Fase 7 — Frontend + Segurança

| Item | Detalhe |
|---|---|
| **Frontend completo** (`index.html`) | Login, chat, upload de documentos, gestão de usuários (ADMIN), feedback 👍👎 |
| **SecurityConfig corrigido** | `authenticationEntryPoint` retorna 401 (não 403) para anônimos |
| **GlobalExceptionHandler** | Handlers para `HttpMediaTypeNotSupportedException` (415) e `MissingServletRequestParameterException` (400) |
| **GroqLlmAdapter** | `RetryExhaustedException` desempacotada para expor a causa real ao caller |
| **TestWebSecurityConfig** | Config de teste dual: `TestSecurityConfig` (desabilita segurança) + `TestWebSecurityConfig` (regras reais) |

---

### Fase 8 — Qualidade RAG

| # | Feature | Detalhe |
|---|---|---|
| **#27** | **Busca Híbrida** | `findSimilarHybrid()` combina similaridade coseno (85%) + BM25 `ts_rank` (15%) com fallback automático para busca vetorial se SQL falhar |
| **#28a** | **Re-ranking heurístico** | `ScoredChunk` com `adjustedScore()`: over-fetch (topK×3) → filtra por `minScore=0.10` → penaliza chunks curtos (×0.85) → top-K |
| **#4** | **Controle de tokens** | `user_token_usage` com upsert atômico; `checkLimit()` lança HTTP 429; `record()` fire-and-forget; `GET /api/users/me/usage` expõe uso/limite/percentual |

---

### Bugs corrigidos nesta sessão

| Bug | Causa | Fix |
|---|---|---|
| Chat retornava "sem resposta" | `PgTokenUsageAdapter.queryForObject()` lança `EmptyResultDataAccessException` para usuário novo (sem linha na tabela) | Trocado por `query() + orElse(0L)` |
| Busca híbrida falhava | `to_tsvector('portuguese')` não existe na imagem Docker padrão | Trocado por `'simple'` (universal) + fallback try-catch |
| Score abaixo do threshold | `ts_rank` (0.01–0.05) derrubava score combinado abaixo de 0.30 | `min-score`: 0.30→0.10; `semantic-weight`: 0.70→0.85 |

---

## 📐 Arquitetura atual

```
Browser (index.html)
    │
    ▼
ChatController / DocumentController / UserController / AuthController
    │
    ▼
QueryChatService (pipeline RAG)
  1. TokenUsageService.checkLimit()        ← #4 bloqueia se quota esgotada
  2. embeddingPort.embed(question)         ← Google Gemini embedding-001 (3072-dim)
  3. VectorStorePort.findSimilarHybrid()   ← #27 vetorial 85% + BM25 15%
     └── fallback: findSimilar()           ← busca vetorial pura se híbrida falhar
  4. ScoredChunk.adjustedScore()           ← #28a penaliza chunks curtos
  5. filtro minScore=0.10 + top-3
  6. PiiMaskingService.maskWithStats()     ← mascara CPF/email/telefone/valor
  7. LlmPort.complete(context, history)    ← Groq llama-3.3-70b-versatile
  8. TokenUsageService.record()            ← registra tokens (fire-and-forget)
    │
    ▼
PostgreSQL (pgvector/pgvector:pg16)
  - documents (status ACTIVE/DELETED)
  - chunks (embedding vector(3072), content_tsvector GENERATED)
  - user_token_usage (upsert atômico por username+yearMonth)
  - chat_history, audit_log, rag_interactions, feedback
```

---

## 🗂️ Migrations aplicadas

| Versão | Descrição |
|---|---|
| V1 | Tabelas base: documents + chunks (vector 1536) |
| V2 | Upgrade embedding → 3072 dims (Gemini); TRUNCATE chunks |
| V3 | Tabela users + JWT |
| V4 | Soft delete: coluna `status` em documents + chunks |
| V5 | RAG observability: rag_interactions + feedback |
| V6 | Audit log |
| V7 | Chat history: chat_sessions + chat_messages |
| V8 | `content_tsvector` GENERATED para BM25 (corrigida em V10) |
| V9 | `user_token_usage` para controle de quota |
| V10 | Recria `content_tsvector` com `'simple'` (portabilidade) |

---

## 🧪 Cobertura de testes (115 casos)

| Suite | Casos |
|---|---|
| AuthControllerTest | 5 |
| FeedbackControllerTest | 4 |
| RequestIdFilterTest | 4 |
| SecurityEndpointsTest | 10 |
| GroqLlmAdapterResilienceTest | 5 |
| AuditLogServiceTest | 6 |
| AuthServiceTest | 8 |
| ChatHistoryServiceTest | 9 |
| ChunkingStrategyTest | 3 |
| PiiMaskingServiceTest | 14 |
| QueryChatServiceTest | 4 |
| RateLimiterServiceTest | 7 |
| ResponseClassifierTest | 7 |
| SoftDeleteServiceTest | 3 |
| **TokenUsageServiceTest** | **7** ← novo |
| UserServiceTest | 6 |
| RagBotHealthIndicatorTest | 4 |
| JwtTokenProviderTest | 9 |
| **Total** | **115** |

---

## 📋 Pendências para próximas sessões

### Fase 9 — Confiabilidade (recomendada para próxima sessão)
- [ ] **#15** Testes unitários: cobertura ≥80% via JaCoCo
- [ ] **#16** Testes de integração com **Testcontainers** (PostgreSQL real nas migrations e queries vetoriais)

### Fase 10 — Features avançadas
- [ ] **#22** Ingestão assíncrona (upload retorna imediato; chunking/embedding em background)
- [ ] **#25** Config IA via tela admin (provider, top-k, chunk-size sem restart)
- [ ] **#26** Versionamento de documentos
- [ ] **#33** Namespaces por departamento

### Dívidas frontend abertas
- [ ] Chat usar `sessionId` do banco (hoje usa histórico em memória)
- [ ] Painel "Histórico de Conversas"
- [ ] Painel "Auditoria" (ADMIN)
- [ ] Dashboard de qualidade RAG (#10)
