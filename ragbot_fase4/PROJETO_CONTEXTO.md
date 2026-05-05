# RAGBot — Contexto do Projeto para Claude

> **USO:** Cole este arquivo no início de cada sessão para economizar tokens.
> Última atualização: Fase 7 concluída (Frontend completo + dashboard RAG + auditoria + segurança 401)

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5 |
| Segurança | Spring Security + JWT (jjwt 0.12.6) + BCrypt |
| IA (LLM) | Groq API (llama-3.3-70b-versatile) |
| IA (Embedding) | Google Gemini (gemini-embedding-001) |
| Banco | PostgreSQL 16 + pgvector (3072 dim) |
| Migrations | Flyway (V1–V7) |
| Proxy | Nginx (HTTPS + headers segurança) |
| Container | Docker multi-stage + docker-compose prod |
| Testes | JUnit 5 + Mockito + MockWebServer + JaCoCo |

---

## Arquitetura

```
domain/           → entities (record puro, zero framework)
application/
  port/in/        → IngestDocumentUseCase, QueryChatUseCase (retorna ChatResult com sessionId),
                     ListDocumentsUseCase, AuthUseCase, ManageUsersUseCase
  port/out/       → EmbeddingPort, LlmPort, VectorStorePort, DocumentParserPort,
                     DocumentStoragePort, UserRepositoryPort, RagObservabilityPort,
                     AuditLogPort, ChatHistoryPort
  service/        → IngestDocumentService, QueryChatService, ListDocumentsService,
                     AuthService, UserService, RateLimiterService,
                     PiiMaskingService, ResponseClassifier, RagObservabilityService,
                     AuditLogService, ChatHistoryService
adapter/
  in/rest/        → AuthController, DocumentController, ChatController,
                     UserController, FeedbackController, AdminController,
                     GlobalExceptionHandler
  out/ai/         → groq/, google/, copilot/
  out/auth/       → UserRepositoryAdapter
  out/audit/      → PgAuditLogAdapter
  out/chat/       → PgChatHistoryAdapter
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
record Document(String id, String fileName, String mimeType,
                String storageReference, long fileSizeBytes, Instant ingestedAt)
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
                   DOCUMENT_UPLOAD, DOCUMENT_DELETE,
                   ROLE_CHANGE, USER_DEACTIVATE, USER_ACTIVATE }
```

---

## Endpoints REST

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | /api/auth/login | público | Retorna JWT |
| POST | /api/auth/register | público | Cria usuário |
| GET | /api/documents | VIEWER+ | Lista docs ACTIVE |
| POST | /api/documents | EDITOR+ | Upload PDF/CSV |
| DELETE | /api/documents/{id} | ADMIN | Soft delete |
| POST | /api/chat | VIEWER+ | Chat → {answer, interactionId, sessionId} |
| GET | /api/chat/sessions | VIEWER+ | Lista sessões do usuário |
| GET | /api/chat/sessions/{id} | VIEWER+ | Mensagens de uma sessão |
| POST | /api/feedback | VIEWER+ | Feedback 👍/👎 {interactionId, positive} |
| GET | /api/users | ADMIN | Lista usuários |
| PUT | /api/users/{id}/role | ADMIN | Altera perfil |
| PUT | /api/users/{id}/deactivate | ADMIN | Desativa |
| PUT | /api/users/{id}/activate | ADMIN | Reativa |
| GET | /api/admin/audit-log | ADMIN | Audit log (filtros: actor, action, limit) |
| GET | /api/admin/dashboard | ADMIN | Métricas RAG (interactions, feedback, rate, tempo) |
| GET | /actuator/health | público | Health geral |
| GET | /actuator/health/ragbot | auth | Docs/chunks indexados |

---

## QueryChatUseCase — assinatura atual

```java
// sessionId é opcional no request; retornado sempre na resposta
ChatResult queryWithSession(String question, String sessionId, String username);
ChatResult query(String question, List<ChatMessage> history, String username); // compat.

record ChatResult(String answer, String interactionId, String sessionId) {}
```

---

## Banco de Dados — Migrations

| Arquivo | O que faz |
|---|---|
| V1 | documents, chunks (vector 1536), índice IVFFLAT |
| V2 | chunks: vector(1536→3072) |
| V3 | tabela users + admin padrão (Admin@123) |
| V4 | coluna status ACTIVE/DELETED em documents e chunks |
| V5 | tabela rag_interactions (observabilidade) |
| V6 | tabela audit_log (actor, action, target, ip, created_at) |
| V7 | tabelas chat_sessions + chat_messages (histórico persistido) |

**Próxima migration:** V8

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
| Audit Log | AuditLogService — LOGIN, REGISTER, UPLOAD, DELETE, ROLE_CHANGE, ACTIVATE/DEACTIVATE |
| Chat History | ChatHistoryService + PgChatHistoryAdapter — sessões e mensagens persistidas no banco |

---

## Configuração (chaves principais)

```yaml
ragbot.ai.provider: groq
ragbot.chunking.size: 300 / overlap: 50
ragbot.retrieval.top-k: 2
ragbot.security.jwt.secret: ${JWT_SECRET}
ragbot.rate-limit.user-per-minute: 20 / user-per-day: 500
groq.token: ${GROQ_API_KEY}
groq.timeout-seconds: 30 / max-retries: 3
google.token: ${GOOGLE_API_KEY}
management.endpoints.web.exposure.include: health,info,metrics
```

---

## Variáveis de Ambiente

```powershell
$env:GROQ_API_KEY   = "gsk_..."
$env:GOOGLE_API_KEY = "AIza..."
$env:JWT_SECRET     = "min-32-chars!!"
```

---

## Testes (19 arquivos, ~110 casos)

| Arquivo | Casos |
|---|---|
| JwtTokenProviderTest | 9 |
| AuthServiceTest | 8 (+1 audit) |
| UserServiceTest | 6 |
| AuthControllerTest | 5 |
| SecurityEndpointsTest | 10 |
| RequestIdFilterTest | 4 |
| RateLimiterServiceTest | 7 |
| GroqLlmAdapterResilienceTest | 5 |
| ChunkingStrategyTest | 3 |
| QueryChatServiceTest | 4 (+1 session) |
| PiiMaskingServiceTest | 14 |
| SoftDeleteServiceTest | 3 |
| RagBotHealthIndicatorTest | 4 |
| ResponseClassifierTest | 7 |
| FeedbackControllerTest | 4 |
| **AuditLogServiceTest** | **6** |
| **ChatHistoryServiceTest** | **8** |

**Rodar:** `./mvnw clean test`

---

## Roadmap — Status

### ✅ Concluído

1, 2, 3 (Segurança) · 5, 6 (Custos) · 7, 8, 9 (Observabilidade RAG) ·
11→bugfix AppConfig · 12 (Request ID) · 18, 19, 20, 21 (Infra) ·
29 (Soft delete) · 31 (PII/LGPD) · **#11 (Audit Log) · #13 (Chat History no banco)** ·
**Fase 7: Dashboard RAG + Auditoria + Histórico + Frontend completo**

### 🔲 Pendente (por prioridade)

| # | Item | Esforço |
|---|---|---|
| 4 | Controle de tokens por sessão/usuário | 🟡 |
| 15 | Testes unitários completos | 🔴 |
| 16 | Testes integração (Testcontainers) | 🔴 |
| 22 | Fila ingestão assíncrona | 🟡 |
| 25 | Config IA via tela | 🟡 |
| 26 | Versionamento de documentos | 🔴 |
| 27 | Busca Híbrida (vetorial + BM25) | 🟡 |
| 28a | Re-ranking heurísticas | 🟢 |
| 30 | Multi-tenancy | 🔴 |
| 33 | Namespaces/coleções | 🟡 |

---

## Combinações próximas sessões

| Combinação | Esforço | Notas |
|---|---|---|
| **#10** | 🟡 | Dashboard de qualidade RAG na tela |
| **#4** | 🟡 | Controle de tokens por usuário |
| **#27 + #28a** | 🟡🟢 | Busca híbrida + re-ranking |
| **#15 + #16** | 🔴 | Cobertura de testes completa |

---

## Regras de desenvolvimento

1. TDD obrigatório — testes antes da implementação
2. domain e application nunca importam Spring/banco/IA
3. Chaves de API sempre via variável de ambiente
4. Soft delete: filtrar WHERE status='ACTIVE' em documents e chunks
5. PII: nunca enviar texto não-mascarado a APIs externas
6. QueryChatUseCase.queryWithSession() recebe sessionId (nullable) e retorna ChatResult com sessionId
7. AuditLogService.record() nunca lança exceção — falha silenciosamente no log
8. SecurityConfig: authenticationEntryPoint configurado para retornar 401 (não 403) para anônimos
9. @WebMvcTest com segurança real: usar TestWebSecurityConfig (@Import SecurityConfig); sem segurança: TestSecurityConfig

## Iniciar

```powershell
docker-compose up -d
$env:GROQ_API_KEY="gsk_..."; $env:GOOGLE_API_KEY="AIza..."; $env:JWT_SECRET="32chars!!"
./mvnw clean test && ./mvnw spring-boot:run
# Login: admin / Admin@123
```
