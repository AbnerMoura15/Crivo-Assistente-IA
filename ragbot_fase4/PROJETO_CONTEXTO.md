# Crivo AI (RAGBot) — Contexto do Projeto para Claude

> **USO:** Cole este arquivo no início de cada sessão para economizar tokens.
> Última atualização: 2026-05-06 — Fase 12 completa
> Testes: **254 total (195 ✅ + 59 skip Docker)** | Cobertura: **≥80% JaCoCo** ativo

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5 |
| Segurança | Spring Security + JWT (jjwt 0.12.6) + BCrypt |
| IA (LLM) | Groq API (llama-3.3-70b-versatile) |
| IA (Embedding) | Google Gemini (gemini-embedding-001, 3072 dim) |
| Banco | PostgreSQL 16 + pgvector — porta **5544**, banco **ragbot** |
| Migrations | Flyway (**V1–V15**) — próxima: V16 |
| Frontend | SPA embutida no Spring Boot (src/main/resources/static/index.html) |
| Proxy | Nginx (HTTPS + headers segurança) — apenas em prod |
| Container | Docker multi-stage + docker-compose.yml + docker-compose.prod.yml |
| Testes | JUnit 5 + Mockito + MockWebServer + Testcontainers + JaCoCo |

---

## Identidade / Branding

- **Nome do produto:** Crivo AI
- **Assistente:** Abner (avatar `✦`, título "Bem-vindo ao Crivo.")
- **Frontend:** dark/light mode, markdown rendering, source chips, empty state Abner

---

## Arquitetura (Hexagonal)

```
domain/           → entities (record puro, zero framework)
application/
  port/in/        → IngestDocumentUseCase, QueryChatUseCase, ListDocumentsUseCase,
                     AuthUseCase, ManageUsersUseCase, ManageNamespacesUseCase
  port/out/       → EmbeddingPort, LlmPort, VectorStorePort, DocumentParserPort,
                     DocumentStoragePort, UserRepositoryPort, RagObservabilityPort,
                     AuditLogPort, ChatHistoryPort, TokenUsagePort, RagConfigPort
  service/        → IngestDocumentService, QueryChatService, ListDocumentsService,
                     AuthService, UserService, RateLimiterService,
                     PiiMaskingService, ResponseClassifier, RagObservabilityService,
                     AuditLogService, ChatHistoryService, TokenUsageService,
                     RagConfigService, AsyncIngestWorker
adapter/
  in/rest/        → AuthController, DocumentController, ChatController,
                     UserController, FeedbackController, AdminController,
                     NamespaceController, RagConfigController,
                     GlobalExceptionHandler
  out/ai/         → GroqLlmAdapter, GoogleEmbeddingAdapter, CopilotLlmAdapter
  out/auth/       → UserRepositoryAdapter
  out/audit/      → PgAuditLogAdapter
  out/chat/       → PgChatHistoryAdapter
  out/config/     → PgRagConfigAdapter
  out/observability → RagObservabilityAdapter
  out/parser/     → PdfParserAdapter, CsvParserAdapter
  out/storage/    → LocalDocumentStorageAdapter
  out/vectorstore → PgVectorStoreAdapter
config/
  security/       → SecurityConfig (@EnableMethodSecurity), JwtTokenProvider,
                     JwtAuthenticationFilter
  filter/         → RequestIdFilter (Order 1), RateLimitFilter (Order 2)
  health/         → RagBotHealthIndicator
```

---

## Entidades de Domínio

```java
record Document(String id, String fileName, String mimeType,
                String storageReference, long fileSizeBytes, Instant ingestedAt,
                String namespaceId, int version, String originalDocumentId,
                String processingStatus)   // PROCESSING | READY | FAILED

record Namespace(String id, String slug, String displayName, Instant createdAt)
    // DEFAULT_ID = "00000000-0000-0000-0000-000000000010"

record RagConfig(int topK, double minScore, int shortChunkLength,
                 boolean hybridEnabled, double semanticWeight,
                 long monthlyTokenLimit, int maxHistoryMessages)

record Chunk(String id, String documentId, String content,
             List<Float> embedding, Map<String,String> metadata)
record ScoredChunk(Chunk chunk, double score)
    // adjustedScore(shortChunkLen): penaliza chunks curtos (score * 0.85)

record ChatMessage(String role, String content, Instant timestamp)
record ChatSession(String id, String username, Instant createdAt,
                   Instant lastMessageAt, List<ChatMessage> messages)

record User(String id, String username, String email, String passwordHash,
            UserRole role, boolean active, Instant createdAt, Instant lastLogin)
record AuditEntry(String id, String actor, AuditAction action,
                  String targetId, String targetType,
                  String detail, String ipAddress, Instant createdAt)

enum UserRole    { VIEWER, EDITOR, ADMIN }
enum AuditAction { LOGIN_SUCCESS, LOGIN_FAILED, REGISTER,
                   DOCUMENT_UPLOAD, DOCUMENT_DELETE, ROLE_CHANGE,
                   USER_DEACTIVATE, USER_ACTIVATE, USER_CREATE,
                   NAMESPACE_CREATE, NAMESPACE_DELETE, CONFIG_UPDATE }
// document status strings: 'ACTIVE', 'DELETED', 'SUPERSEDED'
```

---

## Assinaturas-chave

```java
// QueryChatUseCase
ChatResult queryWithSession(String question, String sessionId, String username, String namespaceId);
ChatResult query(String question, List<ChatMessage> history, String username, String namespaceId);
// defaults retroativos usam Namespace.DEFAULT_ID

record ChatResult(String answer, String interactionId, String sessionId, List<String> sources) {
    public ChatResult(String answer, String interactionId, String sessionId) { ... } // compat
}

// REST — ChatResponse inclui sources
record ChatResponse(String answer, String interactionId, String sessionId, List<String> sources)

// IngestDocumentUseCase
IngestResult ingest(String fileName, String contentType, InputStream is,
                    long size, String namespaceId);
record IngestResult(String documentId, int version, String processingStatus)

// ListDocumentsUseCase
List<Document> listByNamespace(String namespaceId);
List<Document> listAll();
Optional<Document> findById(String id);
List<Document> listVersionHistory(String documentId);
boolean retryProcessing(String documentId);
void delete(String id);

// VectorStorePort
List<ScoredChunk> findSimilar(List<Float> embedding, int topK, String namespaceId);
List<ScoredChunk> findSimilarHybrid(List<Float> embedding, String query,
                                     int topK, double semanticWeight, String namespaceId);

// RagConfigService
RagConfig cfg = ragConfigService.getConfig(); // cache 30s, nunca null

// ChatHistoryService
ChatSession getOrCreateSession(String sessionId, String username);
List<ChatMessage> loadRecentMessages(String sessionId); // respeita maxHistoryMessages
boolean deleteSession(String sessionId, String username);
```

---

## Endpoints REST

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | /api/auth/login | público | Retorna JWT |
| POST | /api/auth/register | público | Cria usuário |
| GET | /api/documents?namespaceId= | VIEWER+ | Lista docs ACTIVE |
| POST | /api/documents?namespaceId= | EDITOR+ | Upload PDF/CSV → 202; auto-versiona |
| DELETE | /api/documents/{id} | ADMIN | Soft delete |
| GET | /api/documents/{id}/status | VIEWER+ | Status PROCESSING/READY/FAILED |
| GET | /api/documents/{id}/versions | VIEWER+ | Histórico de versões |
| POST | /api/documents/{id}/retry | EDITOR+ | Reprocessa doc FAILED → 202 |
| POST | /api/chat | VIEWER+ | Chat → {answer, interactionId, sessionId, sources} |
| GET | /api/chat/sessions | VIEWER+ | Lista sessões do usuário |
| GET | /api/chat/sessions/{id} | VIEWER+ | Mensagens de uma sessão (owner only) |
| DELETE | /api/chat/sessions/{id} | VIEWER+ | Remove sessão (owner only) |
| POST | /api/feedback | VIEWER+ | Feedback 👍/👎 por interactionId |
| GET | /api/users/me/usage | VIEWER+ | Uso de tokens do próprio usuário |
| GET | /api/users | ADMIN | Lista todos os usuários |
| GET | /api/users/{id} | ADMIN | Busca usuário por ID |
| POST | /api/users | ADMIN | Cria usuário |
| PUT | /api/users/{id}/role | ADMIN | Altera perfil |
| PUT | /api/users/{id}/deactivate | ADMIN | Desativa |
| PUT | /api/users/{id}/activate | ADMIN | Reativa |
| GET | /api/namespaces | VIEWER+ | Lista namespaces |
| POST | /api/namespaces | ADMIN | Cria namespace {slug, displayName} |
| DELETE | /api/namespaces/{id} | ADMIN | Remove (só se vazio) |
| GET | /api/admin/audit-log | ADMIN | Audit log (filtros: actor, action, limit) |
| GET | /api/admin/dashboard | ADMIN | Métricas RAG |
| GET | /api/admin/config | ADMIN | Configuração atual do RAG |
| PUT | /api/admin/config | ADMIN | Salva nova configuração (efeito imediato) |
| GET | /actuator/health | público | Health geral |

---

## Banco de Dados — Migrations Flyway

| Arquivo | O que faz |
|---|---|
| V1 | documents, chunks (vector 1536), índice IVFFLAT |
| V2 | chunks: vector(1536→3072) |
| V3 | tabela users + admin padrão (admin / Admin@123) |
| V4 | coluna status ACTIVE/DELETED em documents e chunks |
| V5 | tabela rag_interactions (observabilidade) |
| V6 | tabela audit_log |
| V7 | tabelas chat_sessions + chat_messages |
| V8 | tabela user_token_usage (user, year_month, tokens_used) |
| V9 | tabela rate_limit_counters |
| V10 | índice BM25/FTS em chunks.content (ts_vector, 'simple') |
| V11 | tabela namespaces + FK namespace_id em documents |
| V12 | colunas version_number, original_document_id; status SUPERSEDED |
| V13 | tabela rag_config (key/value) + 6 seeds de defaults |
| V14 | coluna processing_status (PROCESSING/READY/FAILED) em documents |
| V15 | seed chat.max-history-messages=6 em rag_config |

**Próxima migration:** V16

---

## Regras de Negócio

| Regra | Onde |
|---|---|
| Soft Delete | UPDATE status='DELETED' em documents + chunks |
| PII Masking | QueryChatService → PiiMaskingService antes do LLM |
| Rate Limiting | RateLimitFilter — user 20/min 500/dia; anon 5/min 50/dia |
| Timeout/Retry | GroqLlmAdapter — 30s timeout, 3 retries em 429/5xx |
| Request ID | RequestIdFilter — UUID no MDC + header X-Request-ID |
| Observabilidade | QueryChatService → RagObservabilityService |
| Classificação | ResponseClassifier — ANSWERED / NOT_FOUND |
| Audit Log | AuditLogService — todas as ações; falha silenciosamente |
| Token Limit | TokenUsageService.checkLimit() → 429 TOKEN_LIMIT_EXCEEDED |
| Busca Híbrida | findSimilarHybrid() — vetorial + BM25 (ts_rank 'simple') |
| Re-ranking | ScoredChunk.adjustedScore(shortChunkLength) |
| Namespaces | Chunks isolados por namespace_id via JOIN documents |
| Versionamento | findActiveByFileName() → supersede() → version++ |
| Config IA | RagConfigService (cache 30s) → PgRagConfigAdapter → rag_config |
| Async Ingest | store(PROCESSING) → @Async AsyncIngestWorker → READY/FAILED |
| Truncar Hist | loadRecentMessages() respeita cfg.maxHistoryMessages() (padrão 6) |
| Retry FAILED | POST /retry → retryProcessing() → AsyncIngestWorker |
| Source Chips | QueryChatService extrai chunk.metadata["source"] → distinct → ChatResult.sources |
| 429 distinção | Frontend distingue TOKEN_LIMIT_EXCEEDED vs GROQ_RATE_LIMIT |

---

## Segurança (SecurityConfig)

```java
// URL-level (filtro HTTP — retorna 403 antes de chegar no método):
GET  /api/documents/**  → VIEWER+
POST /api/documents/**  → EDITOR+
DELETE /api/documents/**→ ADMIN
/api/chat/**            → VIEWER+
/api/feedback/**        → VIEWER+
/api/users/me/usage     → authenticated
/api/users/**           → ADMIN
/api/admin/**           → ADMIN
GET  /api/namespaces/** → authenticated
/api/namespaces/**      → ADMIN
// anyRequest           → authenticated

// Method-level (@PreAuthorize) em RagConfigController, NamespaceController (defesa dupla)
// 401 configurado via authenticationEntryPoint (não 403) para não-autenticados
```

---

## Testes (27 arquivos, 254 casos totais)

| Grupo | Arquivos | Casos |
|---|---|---|
| **Controllers** | AuthControllerTest, FeedbackControllerTest, ChatControllerTest, DocumentControllerTest, UserControllerTest, RagConfigControllerTest, NamespaceControllerTest, AdminControllerTest, SecurityEndpointsTest | ~80 |
| **Services** | AuthServiceTest, UserServiceTest, QueryChatServiceTest, ChatHistoryServiceTest, TokenUsageServiceTest, RateLimiterServiceTest, PiiMaskingServiceTest, ResponseClassifierTest, AuditLogServiceTest, ChunkingStrategyTest, SoftDeleteServiceTest, IngestDocumentServiceTest, AsyncIngestWorkerTest | ~90 |
| **Config** | JwtTokenProviderTest, RagBotHealthIndicatorTest, RequestIdFilterTest, GroqLlmAdapterResilienceTest | ~22 |
| **Integration (Testcontainers)** | AbstractIntegrationTest, FlywayMigrationTest, DocumentStorageIntegrationTest, PgVectorStoreIntegrationTest, PgChatHistoryIntegrationTest | 59 (skip sem Docker) |

**JaCoCo:** threshold **≥80% instructions** ativo em `mvn test`
**Exclusões JaCoCo:** `adapter/out/**`, `adapter/in/rest/dto/**`, `config/**`, `domain/exception/**`, `RagBotApplication`

```bash
mvn clean test          # roda tudo + verifica cobertura
mvn test -pl . -Dtest=ChatControllerTest  # roda só um
```

---

## Frontend (index.html — SPA embutida)

- **Branding:** Crivo AI, assistente Abner, avatar `✦`
- **Empty state:** `.abner-avatar`, sugestões via `useHint('string')`
- **Markdown:** `parseMarkdown()` puro JS — h1/h2/h3, bold, italic, código, tabelas GFM, listas
- **Source chips:** `.source-chip` com nome dos arquivos-fonte de cada resposta
- **Loading state:** textarea + botão desabilitados durante request; typing hint "Abner está analisando…"
- **Erros 429:** distingue `TOKEN_LIMIT_EXCEEDED` vs `GROQ_RATE_LIMIT`; restaura texto no textarea
- **Sessões:** `currentSessionId` persistido em `sessionStorage`; chip de sessão no topbar
- **Namespace selector:** `<select>` no sidebar; `currentNamespaceId` usado em todas as requests
- **Tema:** dark/light, persiste em `localStorage`
- **Polls de status:** `pollProcessingStatus()` a cada 1.5s até READY/FAILED

---

## Configuração (application.yml — chaves principais)

```yaml
spring.datasource.url: jdbc:postgresql://localhost:5544/ragbot
spring.datasource.username: postgres / password: postgres

groq.token: ${GROQ_API_KEY}
groq.model: llama-3.3-70b-versatile
groq.timeout-seconds: 30 / max-retries: 3

google.token: ${GOOGLE_API_KEY}
google.embedding-model: gemini-embedding-001

ragbot.security.jwt.secret: ${JWT_SECRET}
ragbot.chunking.size: 300 / overlap: 50
ragbot.rate-limit.user-per-minute: 20
```

---

## Iniciar

```powershell
# 1. Banco (Docker)
docker compose up -d

# 2. Variáveis de ambiente
$env:GROQ_API_KEY  = "gsk_..."
$env:GOOGLE_API_KEY = "AIza..."
$env:JWT_SECRET    = "minimo-32-caracteres-aqui!!"

# 3. API + Frontend
mvn spring-boot:run
# → http://localhost:8080
# Login padrão: admin / Admin@123

# DBeaver: localhost:5544 / banco: ragbot / user: postgres / senha: postgres
```

---

## Roadmap — Status

```
✅ CONCLUÍDO                             🔲 BACKLOG
─────────────────────────────────────    ──────────────────────
Auth JWT + BCrypt                        #30 Multi-tenancy
Rate Limiting (user + anon)
PII Masking (LGPD)
Soft Delete
Observabilidade + Dashboard
Audit Log
Chat History + sessões
Feedback 👍/👎
Docker infra (compose + Nginx)
Busca Híbrida (vetorial + BM25)
Re-ranking heurístico
Token Control (limite mensal, 429)
Namespaces/coleções (#33)
Versionamento de documentos (#26)
Config IA via tela (#25)
Ingestão assíncrona + retry (#22)
Truncamento dinâmico de histórico
Testcontainers (#16)
JaCoCo ≥80% (#6) — 254 testes
Frontend Crivo AI / Abner (Bloco 3+4)
  · Empty state Abner
  · Loading states
  · Markdown + tabelas
  · Source chips
  · 429 distinção + restore
```
