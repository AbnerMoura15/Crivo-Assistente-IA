# Crivo AI — O que ainda falta

> Atualizado em: 2026-05-06 | Estado: **Fase 12 — ~97% concluído**
> Testes: 254 (195 ✅ + 59 skip Docker) | JaCoCo ≥80% ativo

---

## ✅ Tudo concluído

| Fase/# | Itens |
|---|---|
| **1–8** | Auth, Rate Limit, PII, Soft Delete, Observabilidade, Audit Log, Chat History, Feedback, Docker, Frontend base, Busca Híbrida, Re-ranking, Tokens |
| **#33** | Namespaces/coleções |
| **#26** | Versionamento de documentos |
| **#25** | Config IA via tela (sliders, efeito imediato) |
| **#22** | Ingestão assíncrona + retry FAILED |
| **#16** | Testcontainers (PostgreSQL+pgvector real, Flyway V1–V15) |
| **#6** | JaCoCo ≥80% (254 testes, 27 arquivos) |
| **Bloco 3** | Empty state Abner, Loading states, Source chips |
| **Bloco 4** | Markdown + tabelas, 429 distinção, restore textarea |
| **Branding** | Crivo AI / Abner, avatar ✦, suggestion buttons |

---

## 🔲 Backlog (único item restante)

| # | Item | Esforço | Detalhe |
|---|---|---|---|
| **#30** | **Multi-tenancy** | 🔴 Alto | Isolamento completo por organização/tenant; separação total de dados no banco, APIs e frontend |

---

## Credenciais / Inicialização rápida

```powershell
docker compose up -d
$env:GROQ_API_KEY="gsk_..."; $env:GOOGLE_API_KEY="AIza..."; $env:JWT_SECRET="32chars!!"
mvn spring-boot:run
# http://localhost:8080 — admin / Admin@123
```
