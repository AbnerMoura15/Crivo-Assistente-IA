# RAGBot — Contexto do Projeto para Claude

> **USO:** Cole este arquivo no início de cada sessão para economizar tokens.
> Última atualização: Fase 4 concluída (#18, #19, #20, #21)

---

## Stack

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 3.2.5 |
| Segurança | Spring Security + JWT (jjwt 0.12.6) + BCrypt |
| IA (LLM) | Groq API (llama-3.3-70b-versatile) — provider ativo |
| IA (Embedding) | Google Gemini (gemini-embedding-001) |
| Banco | PostgreSQL 16 + pgvector (vetores de 3072 dim) |
| Migrations | Flyway (V1–V4 existentes) |
| Proxy | Nginx (reverse proxy + HTTPS + headers de segurança) |
| Container | Docker multi-stage + docker-compose prod |
| Testes | JUnit 5 + Mockito + MockWebServer + JaCoCo |
| Build | Maven (usar `./mvnw`) |

---

## Arquitetura — Hexagonal (Ports & Adapters)

```
domain/           → entities (record puro, zero framework)
application/
  port/in/        → use cases (interfaces)
  port/out/       → driven ports (interfaces)
  service/        → implementações dos use cases
adapter/
  in/rest/        → controllers REST + filtros HTTP
  out/ai/         → groq/, google/, copilot/
  out/parser/     → PDF, CSV
  out/storage/    → LocalDocumentStorageAdapter
  out/vectorstore → PgVectorStoreAdapter
  out/auth/       → UserRepositoryAdapter
config/
  security/       → SecurityConfig, JwtTokenProvider, JwtAuthenticationFilter
  filter/         → RequestIdFilter (Order 1), RateLimitFilter (Order 2)
  health/         → RagBotHealthIndicator
```

**Regra de ouro:** `domain` e `application` nunca importam Spring, banco ou IA.

---

## Entidades de Domínio

```java
record Document(String id, String fileName, String mimeType,
                String storageReference, long fileSizeBytes, Instant ingestedAt)
record Chunk(String id, String documentId, String content,
             List<Float> embedding, Map<String,String> metadata)
record ChatMessage(String role, String content, Instant timestamp)
record User(String id, String username, String email, String passwordHash,
            UserRole role, boolean active, Instant createdAt, Instant lastLogin)
enum UserRole { VIEWER, EDITOR, ADMIN }
```

---

## Endpoints REST

| Método | Path | Role | Descrição |
|---|---|---|---|
| POST | /api/auth/login | público | Retorna JWT |
| POST | /api/auth/register | público | Cria usuário |
| GET | /api/documents | VIEWER+ | Lista documentos ACTIVE |
| POST | /api/documents | EDITOR+ | Upload PDF/CSV |
| DELETE | /api/documents/{id} | ADMIN | Soft delete doc + chunks |
| POST | /api/chat | VIEWER+ | Chat com PII masking |
| GET | /api/users | ADMIN | Lista usuários |
| PUT | /api/users/{id}/role | ADMIN | Altera perfil |
| PUT | /api/users/{id}/deactivate | ADMIN | Desativa usuário |
| PUT | /api/users/{id}/activate | ADMIN | Reativa usuário |
| GET | /actuator/health | público | Health check geral |
| GET | /actuator/health/ragbot | auth | Docs e chunks indexados |

---

## Filtros HTTP (ordem de execução)

```
Requisição HTTP
  → RequestIdFilter (Order 1) — injeta X-Request-ID no MDC
  → RateLimitFilter (Order 2) — bloqueia se limite atingido
  → JwtAuthenticationFilter  — valida Bearer token
  → SecurityConfig rules     — verifica permissões
  → Controller
```

---

## Banco de Dados — Migrations existentes

| Arquivo | O que faz |
|---|---|
| V1__create_tables.sql | documents, chunks (vector 1536), índice IVFFLAT |
| V2__upgrade_embedding_dimension_to_3072.sql | chunks: vector(1536→3072) |
| V3__create_users.sql | tabela users + admin padrão (senha: Admin@123) |
| V4__soft_delete.sql | coluna status em documents e chunks (ACTIVE/DELETED) |

**Próxima migration:** V5

---

## Regras de negócio implementadas

| Regra | Onde | Detalhe |
|---|---|---|
| Soft Delete | PgVectorStoreAdapter, LocalDocumentStorageAdapter | UPDATE status='DELETED', nunca DELETE físico |
| Filtro ACTIVE | findAll(), findSimilar() | WHERE status='ACTIVE' em todas as queries |
| PII Masking | QueryChatService → PiiMaskingService | Mascara CPF, CNPJ, email, tel, R$ antes do LLM |
| Rate Limiting | RateLimitFilter | user: 20/min 500/dia; anon: 5/min 50/dia |
| Timeout/Retry | GroqLlmAdapter | 30s timeout; 3 retries em 429/5xx com backoff |
| Request ID | RequestIdFilter | UUID em MDC + header X-Request-ID na resposta |

---

## Configuração (application.yml — chaves principais)

```yaml
ragbot.ai.provider: groq
ragbot.chunking.size: 300 / overlap: 50
ragbot.retrieval.top-k: 2
ragbot.security.jwt.secret: ${JWT_SECRET}
ragbot.security.jwt.expiration-seconds: 86400
ragbot.rate-limit.user-per-minute: 20 / user-per-day: 500
ragbot.rate-limit.anon-per-minute: 5  / anon-per-day: 50
groq.token: ${GROQ_API_KEY}
groq.timeout-seconds: 30 / max-retries: 3 / initial-backoff-ms: 500
google.token: ${GOOGLE_API_KEY}
management.endpoints.web.exposure.include: health,info,metrics
```

---

## Variáveis de Ambiente Obrigatórias

```powershell
$env:GROQ_API_KEY   = "gsk_..."
$env:GOOGLE_API_KEY = "AIza..."
$env:JWT_SECRET     = "min-32-chars!!"
$env:POSTGRES_PASSWORD = "senha-segura"   # produção
```

---

## Testes existentes (14 arquivos, 81 casos)

| Arquivo | Casos |
|---|---|
| JwtTokenProviderTest | 9 |
| AuthServiceTest | 7 |
| UserServiceTest | 6 |
| AuthControllerTest | 5 |
| SecurityEndpointsTest | 10 |
| RequestIdFilterTest | 4 |
| RateLimiterServiceTest | 7 |
| GroqLlmAdapterResilienceTest | 5 |
| ChunkingStrategyTest | 3 |
| QueryChatServiceTest | 3 |
| PiiMaskingServiceTest | 14 |
| SoftDeleteServiceTest | 3 |
| RagBotHealthIndicatorTest | 4 |

**Rodar:** `./mvnw clean test`
**Cobertura:** `target/site/jacoco/index.html`

---

## Arquivos de Infraestrutura

| Arquivo | Descrição |
|---|---|
| `Dockerfile` | Multi-stage build (builder + runtime Alpine) |
| `docker-compose.prod.yml` | Stack completa: postgres + app + nginx |
| `.env.example` | Template de variáveis (nunca subir .env no Git) |
| `.gitignore` | Protege .env, certificados, arquivos de build |
| `nginx/nginx.conf` | Reverse proxy + HTTPS + security headers |
| `scripts/backup.sh` | pg_dump + gzip + limpeza de backups antigos |
| `scripts/restore.sh` | Restore interativo com confirmação |

---

## Roadmap — Status

### ✅ Concluído

| # | Item |
|---|---|
| 1 | Spring Security + JWT + BCrypt + tabela users |
| 2 | Perfis VIEWER/EDITOR/ADMIN + proteção de endpoints |
| 3 | Tela de login + menu lateral por perfil |
| 5 | Rate Limiting por usuário e IP |
| 6 | Timeout e retry com backoff no Groq |
| 12 | Request ID (X-Request-ID) + MDC logging |
| 18 | Dockerfile multi-stage + docker-compose produção |
| 19 | HTTPS + Nginx reverse proxy |
| 20 | Health checks + Spring Actuator |
| 21 | Backup PostgreSQL + restore |
| 29 | Soft delete + filtro ACTIVE em documents e chunks |
| 31 | Mascaramento de PII antes de enviar ao LLM (LGPD) |

### 🔲 Pendente (por prioridade)

| # | Item | Esforço | Depende de |
|---|---|---|---|
| 4 | Controle de tokens por sessão/usuário | 🟡 | #1 |
| 7 | Tabelas observabilidade RAG | 🟡 | — |
| 8 | Classificação automática de respostas | 🟢 | #7 |
| 9 | Feedback 👍/👎 | 🟢 | #7 |
| 11 | Audit log | 🟢 | #1 |
| 13 | Histórico de chat no banco | 🟡 | #1 |
| 10 | Dashboard qualidade RAG | 🟡 | #8 #9 |
| 15 | Testes unitários completos | 🔴 | — |
| 16 | Testes integração (Testcontainers) | 🔴 | #15 |
| 17 | Testes contrato REST (MockMvc) | 🟡 | #15 |
| 22 | Fila ingestão assíncrona | 🟡 | — |
| 23 | Cache de embeddings | 🟢 | — |
| 24 | Paginação de documentos | 🟢 | — |
| 25 | Configuração da IA via tela | 🟡 | #2 |
| 26 | Versionamento de documentos | 🔴 | #2 |
| 27 | Busca Híbrida (vetorial + BM25) | 🟡 | — |
| 28a | Re-ranking por heurísticas | 🟢 | — |
| 28b | Re-ranking por modelo | 🔴 | #28a |
| 30 | Multi-tenancy (tenant_id) | 🔴 | #1 |
| 32 | Dead Letter Queue para ingestão | 🟡 | #22 |
| 33 | Namespaces/coleções de documentos | 🟡 | #26 |

---

## Combinações seguras para próximas sessões

| Combinação | Esforço | Notas |
|---|---|---|
| **#7 + #8 + #9** | 🟡🟢🟢 | Observabilidade RAG — feedback de qualidade |
| **#11 + #13** | 🟢🟡 | Audit log + histórico de chat no banco |
| **#4 sozinho** | 🟡 | Controle de custos por usuário |
| **#26 sozinho** | 🔴 | Versionamento de documentos |
| **#15 sozinho** | 🔴 | Testes unitários completos |

---

## Regras de desenvolvimento

1. **TDD obrigatório:** testes antes da implementação
2. **Nunca misturar camadas:** domain sem Spring, application sem banco/IA
3. **Toda chave via variável de ambiente** — nunca hardcoded
4. **Toda nova migration:** `V{N}__{descricao}.sql`
5. **Soft delete:** filtrar `WHERE status = 'ACTIVE'` em documents e chunks
6. **PII:** nunca enviar texto não-mascarado a APIs externas
7. **Novo provedor IA:** adapter em `adapter/out/ai/{nome}/` + `@ConditionalOnProperty`
8. **Ao alterar `application.yml` ou migrations:** avisar explicitamente

---

## Comandos

```powershell
# Desenvolvimento local
docker-compose up -d
$env:GROQ_API_KEY="gsk_..."; $env:GOOGLE_API_KEY="AIza..."; $env:JWT_SECRET="min32chars!!"
./mvnw clean test           # 14 suítes, 81 casos
./mvnw spring-boot:run      # localhost:8080

# Produção
docker build -t ragbot:latest .
cp .env.example .env        # preencher .env
docker compose -f docker-compose.prod.yml up -d

# Backup manual
docker exec ragbot-postgres /app/scripts/backup.sh

# Login padrão: admin / Admin@123  (TROCAR IMEDIATAMENTE!)
```
