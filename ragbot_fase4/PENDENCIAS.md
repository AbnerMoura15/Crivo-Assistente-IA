# RAGBot — O que ainda falta

> Gerado em: 2026-05-05 | Estado atual: Fase 6 concluída

---

## 🔴 Dívidas do frontend (funcionalidades já prontas no backend, sem UI)

Estas features foram implementadas no backend mas **não aparecem em nenhuma tela**:

| Feature | Backend | Frontend | Observação |
|---|---|---|---|
| **Chat History** (#13) | ✅ banco persiste sessões | ❌ ainda usa histórico em memória | O `sessionId` retornado pelo `/api/chat` é ignorado pelo `index.html`. O botão "Limpar" apenas reseta o array local, não cria nova sessão no banco. |
| **Audit Log** (#11) | ✅ `GET /api/admin/audit-log` | ❌ sem view | O endpoint existe e funciona, mas nenhuma tela consome-o. |
| **Sessões de chat** (#13) | ✅ `GET /api/chat/sessions` | ❌ sem view | O usuário não consegue voltar a uma conversa anterior pela UI. |

### O que o Audit Log normalmente usa (além do frontend próprio)

O audit log é tipicamente consumido por **duas frentes complementares**:

1. **Tela administrativa interna** — view "Auditoria" no próprio painel (o que vamos fazer)
2. **Stack de observabilidade externa** — ex:
   - **ELK Stack** (Elasticsearch + Logstash + Kibana): exporta via Logstash ou Filebeat
   - **Grafana + Loki**: os logs do Spring (que já incluem o audit) são ingeridos pelo Loki
   - **SIEM** (Splunk, Wazuh): para ambientes de compliance mais rígido (LGPD, ISO 27001)
   - **Simples**: `SELECT * FROM audit_log ORDER BY created_at DESC LIMIT 100;` direto no banco

Para o escopo atual do RAGBot, **a view na tela é suficiente**.

---

## 🟡 Roadmap pendente (por prioridade sugerida)

### Fase 7 — Frontend completo (fechar dívidas + Dashboard)
| # | Item | Esforço | Detalhe |
|---|---|---|---|
| F1 | Atualizar chat para usar `sessionId` | 🟢 | Salvar `sessionId` em `sessionStorage`; enviar no body; "Limpar" cria nova sessão |
| F2 | Painel "Histórico de Conversas" | 🟢 | Lista sessões do usuário com data e preview da última mensagem |
| F3 | Painel "Auditoria" (ADMIN) | 🟢 | Tabela com filtros por actor/action, paginação |
| **#10** | **Dashboard qualidade RAG** | 🟡 | Métricas: total respostas, % ANSWERED vs NOT_FOUND, feedbacks 👍/👎, top usuários |

### Fase 8 — Qualidade RAG
| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#27** | Busca Híbrida (vetorial + BM25) | 🟡 | Combinar score semântico + keyword; requer pg_bm25 ou tsvector |
| **#28a** | Re-ranking heurístico | 🟢 | Pós-filtragem dos chunks por score mínimo + penalizar chunks muito curtos |
| **#4** | Controle de tokens por usuário | 🟡 | Contador de tokens consumidos por usuário/mês; alerta ao atingir limite |

### Fase 9 — Confiabilidade e testes
| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#15** | Testes unitários completos | 🔴 | Cobrir adapters, controllers e casos-limite; meta: >80% de cobertura (JaCoCo) |
| **#16** | Testes de integração (Testcontainers) | 🔴 | Subir PostgreSQL real nos testes; testar Flyway migrations, queries reais |

### Fase 10 — Features avançadas
| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#22** | Fila de ingestão assíncrona | 🟡 | Subir upload imediatamente; processar chunking/embedding em background |
| **#25** | Config IA via tela | 🟡 | Admin altera provider, top-k, chunk-size sem reiniciar o servidor |
| **#26** | Versionamento de documentos | 🔴 | Re-upload cria nova versão; chunks antigos ficam vinculados à versão anterior |
| **#33** | Namespaces/coleções | 🟡 | Separar documentos por departamento/projeto |
| **#30** | Multi-tenancy | 🔴 | Isolamento completo por tenant (schema separado ou coluna tenant_id) |

---

## 📊 Estado atual dos testes

| Suite | Casos | Status |
|---|---|---|
| AuditLogServiceTest | 6 | ✅ |
| AuthServiceTest | 8 | ✅ |
| ChatHistoryServiceTest | 9 | ✅ |
| ChunkingStrategyTest | 3 | ✅ |
| FeedbackControllerTest | 4 | ⚠️ (contexto Spring — pré-existente) |
| GroqLlmAdapterResilienceTest | 5 | ✅ |
| PiiMaskingServiceTest | 14 | ⚠️ 1 falha pré-existente (`shouldMaskMultipleCpfs`) |
| QueryChatServiceTest | 4 | ✅ |
| RateLimiterServiceTest | 7 | ✅ |
| ResponseClassifierTest | 7 | ✅ |
| SecurityEndpointsTest | 10 | ⚠️ (contexto Spring — pré-existente) |
| AuthControllerTest | 5 | ⚠️ (contexto Spring — pré-existente) |

> ⚠️ Os testes com `@WebMvcTest` falham porque `RateLimitFilter` não é mockado no contexto de teste.  
> Correção entra na **Fase 9** junto com a revisão geral de testes.

---

## 🔧 Débitos técnicos pontuais

| Item | Onde | Prioridade |
|---|---|---|
| `PiiMaskingServiceTest.shouldMaskMultipleCpfs` está falhando | `PiiMaskingService` | 🟡 |
| `RateLimitFilter` não é mockado nos `@WebMvcTest` | `TestSecurityConfig` | 🟡 |
| Frontend envia `history` em memória ao invés de usar `sessionId` | `index.html` linha 625 | 🔴 (dívida ativa) |
| `clearChat()` não avisa o backend da nova sessão | `index.html` linha 670 | 🟡 |

---

## 🗺️ Resumo visual

```
✅ Implementado    ⏳ Próxima fase    🔲 Backlog

[✅ Autenticação JWT]  [✅ Rate Limit]  [✅ PII Mask]
[✅ Soft Delete]       [✅ Observab.]   [✅ Audit Log]
[✅ Chat History DB]   [✅ Feedback]    [✅ Infra Docker]

[⏳ Frontend sessionId]  [⏳ Histórico UI]  [⏳ Audit UI]
[⏳ Dashboard RAG]

[🔲 Busca Híbrida]  [🔲 Re-ranking]    [🔲 Async Ingest]
[🔲 Token Control]  [🔲 Testcontainers] [🔲 Versionamento]
[🔲 Multi-tenancy]  [🔲 Namespaces]
```

---

## ⚡ Sugestão para próxima sessão

**Fase 7 — Frontend completo** é o mais indicado agora porque:

1. Fecha as dívidas abertas do backend já implementado
2. Entrega valor imediato — o usuário consegue ver e usar o que foi feito
3. Junta 4 itens relacionados em uma única sessão coesa:
   - `sessionId` no chat (integração com #13)
   - Painel de histórico de conversas
   - Painel de auditoria para ADMIN (integração com #11)
   - Dashboard de qualidade RAG (#10)
