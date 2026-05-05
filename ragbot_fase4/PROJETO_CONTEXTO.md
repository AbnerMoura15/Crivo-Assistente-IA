# RAGBot — Contexto do Projeto para Claude

> **USO:** Cole este arquivo no início de cada sessão para economizar tokens.
> Última atualização: 2026-05-05 — Fase 10 parcial (#33 Namespaces + #26 Versioning + #25 Config IA)
> Testes: **118 ✅** | Commits: a3c8212 · 3d50ae1 · 25cb09e

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5 |
| Segurança | Spring Security + JWT (jjwt 0.12.6) + BCrypt |
| IA (LLM) | Groq API (llama-3.3-70b-versatile) |
| IA (Embedding) | Google Gemini (gemini-embedding-001) |
| Banco | PostgreSQL 16 + pgvector (3072 dim) — porta **5544**, banco **ragbot** |
| Migrations | Flyway (**V1–V13**) |
| Proxy | Nginx (HTTPS + headers segurança) |
| Container | Docker multi-stage + docker-compose prod |
| Testes | JUnit 5 + Mockito + MockWebServer + JaCoCo |

---

## Arquitetura

```
domain/           → entities (record puro, zero framework)
application/
  port/in/        → IngestDocumentUseCase, QueryChatUseCase, ListDocumentsUseCase,
                     AuthUseCase, ManageUsersUseCase
  port/out/       → EmbeddingPort, LlmPort, VectorStorePort, DocumentParserPort,
                     DocumentStoragePort, UserRepositoryPort, RagObservabilityPort,
                     AuditLogPort, ChatHistoryPort, TokenUsagePort, RagConfigPort
  service/        → IngestDocumentService, QueryChatService, ListDocumentsService,
                     AuthService, UserService, RateLimiterService,
                     PiiMaskingService, ResponseClassifier, RagObservabilityService,
                     AuditLogService, ChatHistoryService, TokenUsageService,
                     RagConfigService
adapter/
  in/rest/        → AuthController, DocumentController, ChatController,
                     UserController, FeedbackController, AdminController,
                     NamespaceController, RagConfigController,
                     GlobalExceptionHandler
  out/ai/         → groq/, google/, copilot/
  out/auth/       → UserRepositoryAdapter
  out/audit/      → PgAuditLogAdapter
  out/chat/       → PgChatHistoryAdapter
  out/config/     → PgRagConfigAdapter
  out/observability → RagObservabilityAdapter
  out/parser/     → PdfParserAdapter, CsvParserAdapter
  out/storage/    → LocalDocumentStorageAdapter
  out/vectorstore → PgVectorStoreAdapter
config/
  security/       → SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
  filter/         → RequestIdFilter (Order 1), RateLimitFilter (Order 2)
  health/         → RagBotHealthIndicator
```

---

## Entidades de Domínio

```java
// #26 — versioning
record Document(String id, String fileName, String mimeType,
                String storageReference, long fileSizeBytes, Instant ingestedAt,
                String namespaceId, int version, String originalDocumentId)
    // helpers: isFirstVersion(), rootId()

// #33 — namespaces
record Namespace(String id, String slug, String displayName, Instant createdAt)
    // constante: DEFAULT_ID = "00000000-0000-0000-0000-000000000010"

// #25 — config dinâmica do RAG
record RagConfig(int topK, double minScore, int shortChunkLength,
                 boolean hybridEnabled, double semanticWeight, long monthlyTokenLimit)
    // fábrica: RagConfig.defaults()

record Chunk(String id, String documentId, String content,
             List<Float> embedding, Map<String,String> metadata)
record ChatMessage(String role, String content, Instant timestamp)
record ChatSession(String id, String username, Instant createdAt,
                   Instant lastMessageAt, List<ChatMessage> messages)
record User(String id, String username, String email, String passwordHash,
            UserRole role, boolean active, Instant createdAt, Instant lastLogin)
record RagInteraction(String id, String username, String question, String answer,
                      String classification, List<String> sourceDocuments,
                      int chunksRetrieved, Boolean feedbackPositive, Instant createdAt)
record AuditEntry(String id, String actor, AuditAction action,
                  String targetId, String targetType,
                  String detail, String ipAddress, Instant createdAt)
enum UserRole { VIEWER, EDITOR, ADMIN }
enum AuditAction { LOGIN_SUCCESS, LOGIN_FAILED, REGISTER,
                   DOCUMENT_UPLOAD, DOCUMENT_DELETE, ROLE_CHANGE,
                   USER_DEACTIVATE, USER_ACTIVATE, USER_CREATE,
                   NAMESPACE_CREATE, NAMESPACE_DELETE, CONFIG_UPDATE }
// document status: 'ACTIVE', 'DELETED', 'SUPERSEDED'
```

---

## Endpoints REST

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | /api/auth/login | público | Retorna JWT |
| POST | /api/auth/register | público | Cria usuário |
| GET | /api/documents?namespaceId= | VIEWER+ | Lista docs ACTIVE no namespace |
| POST | /api/documents?namespaceId= | EDITOR+ | Upload PDF/CSV; auto-versiona se mesmo nome |
| DELETE | /api/documents/{id} | ADMIN | Soft delete |
| GET | /api/documents/{id}/versions | VIEWER+ | Histórico de versões do documento |
| POST | /api/chat | VIEWER+ | Chat → {answer, interactionId, sessionId} |
| GET | /api/chat/sessions | VIEWER+ | Lista sessões do usuário |
| GET | /api/chat/sessions/{id} | VIEWER+ | Mensagens de uma sessão |
| POST | /api/feedback | VIEWER+ | Feedback 👍/👎 |
| GET | /api/namespaces | VIEWER+ | Lista namespaces |
| POST | /api/namespaces | ADMIN | Cria namespace {slug, displayName} |
| DELETE | /api/namespaces/{id} | ADMIN | Remove namespace (só se vazio) |
| GET | /api/users | ADMIN | Lista usuários |
| PUT | /api/users/{id}/role | ADMIN | Altera perfil |
| PUT | /api/users/{id}/deactivate | ADMIN | Desativa |
| PUT | /api/users/{id}/activate | ADMIN | Reativa |
| POST | /api/users | ADMIN | Cria usuário (admin) |
| GET | /api/admin/audit-log | ADMIN | Audit log (filtros: actor, action, limit) |
| GET | /api/admin/dashboard | ADMIN | Métricas RAG |
| GET | /api/admin/config | ADMIN | Configuração atual do RAG |
| PUT | /api/admin/config | ADMIN | Salva nova configuração RAG (efeito imediato) |
| GET | /actuator/health | público | Health geral |

---

## Banco de Dados — Migrations

| Arquivo | O que faz |
|---|---|
| V1 | documents, chunks (vector 1536), índice IVFFLAT |
| V2 | chunks: vector(1536→3072) |
| V3 | tabela users + admin padrão (Admin@123) |
| V4 | coluna status ACTIVE/DELETED em documents e chunks |
| V5 | tabela rag_interactions (observabilidade) |
| V6 | tabela audit_log |
| V7 | tabelas chat_sessions + chat_messages |
| V8 | tabela token_usage (user, year_month, tokens_used) |
| V9 | tabela rate_limit_counters |
| V10 | índice BM25/FTS em chunks.content (ts_vector, 'simple') |
| V11 | tabela namespaces + FK namespace_id em documents (#33) |
| V12 | colunas version_number, original_document_id em documents; status SUPERSEDED (#26) |
| V13 | tabela rag_config (key/value) + 6 seeds de defaults (#25) |

**Próxima migration:** V14

---

## Regras de negócio implementadas

| Regra | Onde |
|---|---|
| Soft Delete | PgVectorStoreAdapter, LocalDocumentStorageAdapter — UPDATE status='DELETED' |
| Filtro ACTIVE | findAll(), findSimilar() — WHERE status='ACTIVE' |
| PII Masking | QueryChatService → PiiMaskingService — antes do LLM |
| Rate Limiting | RateLimitFilter — user 20/min 500/dia; anon 5/min 50/dia |
| Timeout/Retry | GroqLlmAdapter — 30s timeout, 3 retries em 429/5xx |
| Request ID | RequestIdFilter — UUID no MDC + X-Request-ID na resposta |
| Observabilidade | QueryChatService → RagObservabilityService → RagObservabilityAdapter |
| Classificação | ResponseClassifier — ANSWERED / NOT_FOUND |
| Feedback | FeedbackController → RagObservabilityService.recordFeedback() |
| Audit Log | AuditLogService — todas as ações; falha silenciosamente |
| Chat History | ChatHistoryService + PgChatHistoryAdapter — sessões e mensagens persistidas |
| Token Limit | TokenUsageService — checkLimit() lança 429; record() é fire-and-forget |
| Busca Híbrida | VectorStorePort.findSimilarHybrid() — vetorial + BM25 (ts_rank 'simple') |
| Re-ranking | ScoredChunk.adjustedScore(shortChunkLength) — penaliza chunks curtos |
| Namespaces | Chunks isolados por namespace_id via JOIN documents; default namespace seed |
| Versionamento | IngestDocumentService — findActiveByFileName() → supersede() → increment version |
| Config IA | RagConfigService (cache 30s) → PgRagConfigAdapter → tabela rag_config |

---

## Configuração (chaves principais)

```yaml
spring.datasource.url: jdbc:postgresql://localhost:5544/ragbot
spring.datasource.username: postgres
spring.datasource.password: postgres

ragbot.ai.provider: groq
ragbot.chunking.size: 300 / overlap: 50
ragbot.security.jwt.secret: ${JWT_SECRET}
ragbot.rate-limit.user-per-minute: 20

groq.token: ${GROQ_API_KEY}
groq.timeout-seconds: 30 / max-retries: 3

google.token: ${GOOGLE_API_KEY}
google.embedding-model: gemini-embedding-001
```

---

## QueryChatUseCase — assinatura atual

```java
// 4 args = com namespaceId (#33)
ChatResult queryWithSession(String question, String sessionId, String username, String namespaceId);
ChatResult query(String question, List<ChatMessage> history, String username, String namespaceId);

// default retroativos usam Namespace.DEFAULT_ID
ChatResult queryWithSession(String question, String sessionId, String username);
ChatResult query(String question, List<ChatMessage> history, String username);

record ChatResult(String answer, String interactionId, String sessionId) {}
```

## VectorStorePort — assinatura atual

```java
// primários com namespaceId (#33)
List<ScoredChunk> findSimilar(List<Float> embedding, int topK, String namespaceId);
List<ScoredChunk> findSimilarHybrid(List<Float> embedding, String query,
                                     int topK, double semanticWeight, String namespaceId);
// default retroativos usam Namespace.DEFAULT_ID
List<ScoredChunk> findSimilar(List<Float> embedding, int topK);
```

## RagConfigService — uso

```java
// injeção nos serviços:
RagConfig cfg = ragConfigService.getConfig(); // cache 30s, nunca null
cfg.topK(); cfg.minScore(); cfg.hybridEnabled(); cfg.semanticWeight();
cfg.shortChunkLength(); cfg.monthlyTokenLimit();
```

---

## Testes (19 arquivos, 118 casos)

| Arquivo | Casos |
|---|---|
| JwtTokenProviderTest | 9 |
| AuthServiceTest | 8 |
| UserServiceTest | 9 |
| AuthControllerTest | 5 |
| SecurityEndpointsTest | 10 |
| RequestIdFilterTest | 4 |
| RateLimiterServiceTest | 7 |
| GroqLlmAdapterResilienceTest | 5 |
| ChunkingStrategyTest | 3 |
| QueryChatServiceTest | 4 |
| PiiMaskingServiceTest | 14 |
| SoftDeleteServiceTest | 3 |
| RagBotHealthIndicatorTest | 4 |
| ResponseClassifierTest | 7 |
| FeedbackControllerTest | 4 |
| AuditLogServiceTest | 6 |
| ChatHistoryServiceTest | 9 |
| TokenUsageServiceTest | 7 |

**Rodar:** `./mvnw clean test`

---

## Roadmap — Status

### ✅ Concluído (≈70%)

Fases 1–8 completas + #33 Namespaces + #26 Versionamento + #25 Config IA

### ⏳ Próxima sessão — #22 Ingestão Assíncrona

Upload retorna 202 imediato → chunking/embedding em background thread → status polling.
Ver detalhes em **PENDENCIAS.md**.

### 🔲 Backlog

| # | Item |
|---|---|
| #16 | Testcontainers (integração real com pgvector) |
| #15 | JaCoCo ≥80% |
| #30 | Multi-tenancy |

---

## Iniciar

```powershell
docker-compose up -d
$env:GROQ_API_KEY="gsk_..."; $env:GOOGLE_API_KEY="AIza..."; $env:JWT_SECRET="32chars!!"
./mvnw spring-boot:run
# Login: admin / Admin@123
# DBeaver: localhost:5544 / banco: ragbot / user: postgres / senha: postgres
```
