# RAGBot — O que ainda falta

> Atualizado em: 2026-05-05 | Estado atual: **Fase 10 em andamento** | Testes: 118 ✅
> Commits relevantes: a3c8212 (#33+#26) · 3d50ae1 (#25) · 25cb09e (fix @Autowired)

---

## ✅ Fases / Features concluídas

| Fase/# | Itens entregues |
|---|---|
| **1–6** | Autenticação JWT, Rate Limit, PII Masking, Soft Delete, Observabilidade, Audit Log, Chat History, Feedback, Infra Docker |
| **7** | Frontend completo, segurança 401/403, sessões de chat, histórico, dashboard RAG, auditoria |
| **8** | Busca híbrida (#27), re-ranking heurístico (#28a), controle de tokens (#4) |
| **#33** | Namespaces/coleções — isolamento por projeto/departamento |
| **#26** | Versionamento de documentos — re-upload cria nova versão, status SUPERSEDED |
| **#25** | Config IA via tela — RagConfigService + cache 30s + GET/PUT /api/admin/config + UI com sliders |

---

## 🟡 Roadmap restante (~30%)

### Prioridade ALTA — próxima sessão

| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#22** | **Ingestão assíncrona** | 🟡 Médio | Upload retorna 202 imediato; chunking/embedding em thread pool background; status endpoint GET /api/documents/{id}/status |
| **#16** | **Testes de integração (Testcontainers)** | 🔴 Alto | PostgreSQL + pgvector real nos testes; valida Flyway V1–V13, queries vetoriais, hybrid search |

### Prioridade MÉDIA

| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#15** | Testes unitários — JaCoCo ≥80% | 🟡 | Cobrir adapters (PgVector, LocalStorage, PgRagConfig); relatório HTML |
| **#30** | Multi-tenancy | 🔴 | Isolamento completo por organização/tenant; separação de dados no banco |

---

## ⚡ Próxima sessão recomendada: #22 — Ingestão Assíncrona

### Por que #22?

O upload atual é **síncrono**: a requisição POST /api/documents fica bloqueada até o embedding terminar (~3-10 segundos). Com #22:
- Upload retorna **HTTP 202** imediato com `documentId` e `status: PROCESSING`
- Chunking + embedding rodam em background via `@Async` / `ExecutorService`
- Novo endpoint `GET /api/documents/{id}/status` retorna `PROCESSING | READY | FAILED`
- Frontend mostra spinner com polling até ficar `READY`

### O que precisa ser feito em #22

1. **`V14__document_status.sql`** — adicionar coluna `processing_status` (`PROCESSING`, `READY`, `FAILED`, `ERROR_MSG`) na tabela `documents`
2. **`AsyncIngestService.java`** — recebe `documentId` + `File`, executa parse → chunk → embed → store em thread separada; atualiza status ao final
3. **`IngestDocumentService`** — salva doc com status `PROCESSING`, dispara `AsyncIngestService`, retorna `documentId` imediatamente
4. **`DocumentController`** — POST retorna 202 com body `{documentId, status, fileName}`; novo GET `/{id}/status`
5. **`index.html`** — após upload bem-sucedido inicia polling de status (1s interval) com spinner; atualiza lista quando `READY`

### Configuração sugerida para o thread pool

```yaml
ragbot:
  async:
    core-pool-size: 2
    max-pool-size: 5
    queue-capacity: 50
```

---

## 🗺️ Resumo visual atualizado

```
✅ Implementado    ⏳ Próxima sessão    🔲 Backlog

[✅ Autenticação JWT]    [✅ Rate Limit]       [✅ PII Mask]
[✅ Soft Delete]         [✅ Observab.]        [✅ Audit Log]
[✅ Chat History DB]     [✅ Feedback]         [✅ Infra Docker]
[✅ Busca Híbrida]       [✅ Re-ranking]       [✅ Token Control]
[✅ Frontend completo]   [✅ Namespaces #33]   [✅ Versioning #26]
[✅ Config IA UI #25]

[⏳ Async Ingest #22]   [⏳ Testcontainers #16]

[🔲 JaCoCo #15]         [🔲 Multi-tenancy #30]
```

---

## Credenciais DBeaver (banco do projeto)

| Campo | Valor |
|---|---|
| Host | `localhost` |
| **Porta** | **`5544`** ← não 5432! |
| **Banco** | **`ragbot`** ← não postgres! |
| Usuário | `postgres` |
| Senha | `postgres` |

## Iniciar servidor

```powershell
docker-compose up -d
$env:GROQ_API_KEY="gsk_..."; $env:GOOGLE_API_KEY="AIza..."; $env:JWT_SECRET="32chars!!"
./mvnw spring-boot:run
# Login: admin / Admin@123
```
