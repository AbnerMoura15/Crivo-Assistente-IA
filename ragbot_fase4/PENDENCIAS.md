# RAGBot — O que ainda falta

> Atualizado em: 2026-05-05 | Estado atual: **Fase 10 — ~80% concluído** | Testes: 118 ✅
> Commits: a3c8212 (#33+#26) · 3d50ae1 (#25) · 25cb09e (fix) · 393a84d (#22)

---

## ✅ Concluído

| Fase/# | Itens |
|---|---|
| **1–8** | Autenticação, Rate Limit, PII, Soft Delete, Observabilidade, Audit Log, Chat History, Feedback, Docker, Frontend, Busca Híbrida, Re-ranking, Tokens |
| **#33** | Namespaces/coleções |
| **#26** | Versionamento de documentos |
| **#25** | Config IA via tela (RagConfigService + GET/PUT /api/admin/config + UI sliders) |
| **#22** | Ingestão assíncrona (POST→202, AsyncIngestWorker, polling status, badge ⏳) |

---

## 🟡 Roadmap restante (~20%)

### Prioridade ALTA — próxima sessão

| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#16** | **Testes de integração (Testcontainers)** | 🔴 Alto | PostgreSQL+pgvector real; valida Flyway V1–V14, queries vetoriais, hybrid search end-to-end |
| **#15** | **JaCoCo ≥80%** | 🟡 Médio | Cobrir adapters (PgVector, LocalStorage, PgRagConfig); relatório HTML via `mvn test` |

### Prioridade BAIXA

| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#30** | Multi-tenancy | 🔴 | Isolamento completo por organização/tenant; separação de dados no banco |

---

## ⚡ Próxima sessão recomendada: #16 Testcontainers

### Por que #16?

Com Flyway V1–V14 e queries vetoriais reais sendo críticas, é arriscado fazer mais features sem testes de integração. Testcontainers garante que:
- Migrations Flyway rodam em ordem correta (sem conflito de constraints)
- `findSimilar()` com pgvector funciona com embeddings reais (3072 dim)
- Busca híbrida (BM25 + vetorial) retorna resultados corretos
- `processing_status` e `namespace_id` são lidos corretamente do banco real

### O que implementar em #16

1. **Dependência `pom.xml`:**
   ```xml
   <dependency>
     <groupId>org.testcontainers</groupId>
     <artifactId>postgresql</artifactId>
     <scope>test</scope>
   </dependency>
   ```

2. **`AbstractIntegrationTest.java`** — base class com `@Testcontainers` + `PostgreSQLContainer` + pgvector extension + `@DynamicPropertySource`

3. **`DocumentStorageIntegrationTest.java`** — testa store/findByNamespace/findVersionHistory/updateProcessingStatus

4. **`VectorStoreIntegrationTest.java`** — testa findSimilar e findSimilarHybrid com embeddings reais (mock do embeddingPort, vectors fixos)

5. **`FlywayMigrationTest.java`** — valida que todas as 14 migrations rodam sem erro

---

## 🗺️ Resumo visual

```
✅ Implementado    ⏳ Próxima sessão    🔲 Backlog

[✅ Auth JWT]         [✅ Rate Limit]      [✅ PII Mask]
[✅ Soft Delete]      [✅ Observab.]       [✅ Audit Log]
[✅ Chat History]     [✅ Feedback]        [✅ Docker Infra]
[✅ Busca Híbrida]    [✅ Re-ranking]      [✅ Token Control]
[✅ Frontend]         [✅ Namespaces #33]  [✅ Versioning #26]
[✅ Config IA #25]    [✅ Async Ingest #22]

[⏳ Testcontainers #16]   [⏳ JaCoCo #15]

[🔲 Multi-tenancy #30]
```

---

## Credenciais / Inicialização

```powershell
# DBeaver: localhost:5544 / banco: ragbot / user: postgres / senha: postgres
docker-compose up -d
$env:GROQ_API_KEY="gsk_..."; $env:GOOGLE_API_KEY="AIza..."; $env:JWT_SECRET="32chars!!"
./mvnw spring-boot:run
# Login: admin / Admin@123
```
