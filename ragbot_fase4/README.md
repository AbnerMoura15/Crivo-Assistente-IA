# RAGBot — Assistente de Documentos com IA

Chatbot com arquitetura RAG (Retrieval-Augmented Generation) sobre documentos PDF e CSV.
Construído com **Java 21 + Spring Boot 3**, arquitetura **hexagonal/clean** e **agnóstico ao provedor de IA**.

---

## Arquitetura

```
domain/          ← entidades e exceções puras (sem framework)
application/
  port/in/       ← use cases (interfaces)
  port/out/      ← ports para serviços externos (interfaces)
  service/       ← implementação dos use cases
adapter/
  in/rest/       ← controllers REST + DTOs
  out/ai/        ← adapters de IA (Copilot, OpenAI, Anthropic...)
  out/parser/    ← parsers de PDF e CSV
  out/storage/   ← armazenamento de arquivos
  out/vectorstore/ ← pgvector
```

---

## Pré-requisitos

- Java 21+
- Maven 3.9+
- Docker e Docker Compose

---

## Como rodar

### 1. Suba o banco de dados

```bash
docker-compose up -d
```

### 2. Configure as variáveis de ambiente

```bash
export COPILOT_ENDPOINT=https://api.githubcopilot.com
export COPILOT_TOKEN=seu_token_aqui
export COPILOT_MODEL=gpt-4o
export COPILOT_EMBEDDING_MODEL=text-embedding-3-small
```

Ou edite `src/main/resources/application.yml` diretamente.

### 3. Execute a aplicação

```bash
./mvnw spring-boot:run
```

### 4. Acesse a interface

```
http://localhost:8080
```

---

## API REST

| Método | Endpoint | Descrição |
|--------|----------|-----------|
| `POST` | `/api/documents` | Upload de PDF ou CSV (multipart/form-data, campo `file`) |
| `GET`  | `/api/documents` | Lista documentos indexados |
| `DELETE` | `/api/documents/{id}` | Remove documento e seus chunks |
| `POST` | `/api/chat` | Pergunta ao chatbot |

### Exemplo de chat

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "<sua-senha>"}' | jq -r .token)

curl -X POST http://localhost:8080/api/chat \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"question": "Quais são os procedimentos de segurança?"}'
```

---

## Segurança

### O que está tratado

| Área | Medida | Teste que prova |
|---|---|---|
| Autenticação | Sem auto-cadastro público; usuários são criados por ADMIN (`POST /api/users`). JWT HS256 com segredo obrigatório (≥ 32 bytes, sem default em prod). BCrypt custo 12. | `AuthControllerTest`, `JwtTokenProviderTest`, `ProdProfileConfigTest` |
| Admin do seed | O admin criado pela migration V3 tem senha conhecida. Em prod o app **não sobe** enquanto ela não for trocada via `RAGBOT_ADMIN_PASSWORD`. | `DefaultAdminPasswordGuardTest` |
| Autorização | Regras por perfil (VIEWER / EDITOR / ADMIN). Sessões de chat e feedback são restritos ao dono. | `SecurityEndpointsTest`, `ChatHistoryServiceTest`, `RagObservabilityAdapterTest` |
| Entrada | Pergunta com no máximo 2000 caracteres. Upload só `.pdf`/`.csv`, com tipo real conferido pelo conteúdo (`%PDF-`, texto sem NUL), nome sanitizado e limite de 10 MB em prod. | `ChatControllerTest`, `DocumentControllerTest` |
| Custo / abuso | Rate limit por usuário (20/min, 500/dia) e por IP no login (5/min). `X-Forwarded-For` só é aceito vindo de proxy confiável. Cota mensal de tokens por usuário. | `RateLimitFilterTest`, `RateLimiterServiceTest`, `TokenUsageServiceTest` |
| Prompt injection | Trechos dos documentos vão num bloco `<documentos>` delimitado. Um documento não consegue fechar o bloco, e o prompt manda tratar o conteúdo como dado, com um lembrete após o contexto. PII mascarada antes de ir à LLM. | `QueryChatServiceTest` |
| Erros e logs | A API não devolve stack trace nem mensagem interna (500 genérico). Logs não gravam o texto da pergunta nem trechos de documento. | `ChatControllerTest`, `QueryChatServiceTest` |
| SQL | Todas as queries são parametrizadas (`JdbcTemplate` com `?`). | revisão de código |
| CORS / front | CORS só para origens em `ALLOWED_ORIGINS` (vazio = mesma origem). O front escapa dados do usuário em HTML e em `onclick`. | `CorsTest`, `FrontendXssGuardTest` |
| Configuração | Perfil `prod` sem DEBUG, sem detalhes no health, sem senha default de banco. Docker roda com usuário não-root, e no compose de prod o banco fica só na rede interna. | `ProdProfileConfigTest` |
| Dependências | `mvn org.owasp:dependency-check-maven:check` (com `NVD_API_KEY`) falha com CVSS ≥ 7. | — |

Variáveis obrigatórias em produção: `JWT_SECRET`, `RAGBOT_ADMIN_PASSWORD` (no primeiro deploy), senha do banco, chaves de IA. Ver `.env.example`.

### Limites conhecidos

- **Prompt injection é reduzido, não eliminado.** Nenhuma técnica atual impede 100% que um documento influencie a LLM. O impacto é limitado porque o bot não executa ações (só responde), mas um documento malicioso pode distorcer respostas. Restringir quem faz upload (EDITOR) é parte da defesa.
- **Namespaces não são fronteira de confidencialidade.** Todo usuário autenticado consulta todos os namespaces. Para isolar dados entre áreas ou clientes é preciso ACL por namespace (fora do escopo atual).
- **Rate limit em memória.** Vale para uma instância. Com várias réplicas, precisa de armazenamento compartilhado (ex.: Redis).
- **Upload.** Não há antivírus nem sandbox de parsing. PDFs malformados ou muito grandes dentro do limite de 10 MB podem consumir CPU e memória.
- **Banco.** A tabela `rag_interactions` guarda o texto das perguntas (para observabilidade). Defina retenção e acesso conforme a LGPD.
- **JWT.** Não há revogação de token antes da expiração (24h por padrão). Desativar um usuário bloqueia novos logins, mas não tokens já emitidos.
- **Spring Boot 3.2.x** não recebe mais correções gratuitas. Planejar migração para 3.4+/3.5.

---

## Trocar provedor de IA

Basta mudar `ragbot.ai.provider` no `application.yml`:

```yaml
ragbot:
  ai:
    provider: openai   # ou: copilot | anthropic | azure
```

E criar um novo adapter em `adapter/out/ai/` implementando `LlmPort` e `EmbeddingPort`
com `@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "openai")`.

---

## Integração futura com Microsoft Teams

Para integrar ao Teams, crie um adapter em `adapter/in/teams/`:

1. **Bot Framework SDK** para Java (Microsoft Bot Framework)
2. Implemente um `TeamsMessageAdapter` que recebe mensagens do Teams e chama `QueryChatUseCase`
3. Registre o bot no [Azure Bot Service](https://portal.azure.com)
4. Configure o manifesto do app Teams com o endpoint do seu bot

O core da aplicação **não muda nada** — só um novo adapter de entrada.

---

## Estrutura de pacotes completa

```
src/main/java/com/ragbot/
├── RagBotApplication.java
├── config/
│   ├── AppConfig.java
│   └── WebConfig.java
├── domain/
│   ├── entity/  Document, Chunk, ChatMessage
│   └── exception/ UnsupportedDocumentTypeException, DocumentNotFoundException
├── application/
│   ├── port/in/  IngestDocumentUseCase, QueryChatUseCase, ListDocumentsUseCase
│   ├── port/out/ EmbeddingPort, LlmPort, VectorStorePort, DocumentParserPort, DocumentStoragePort
│   └── service/  IngestDocumentService, QueryChatService, ListDocumentsService, ChunkingStrategy
└── adapter/
    ├── in/rest/  DocumentController, ChatController, GlobalExceptionHandler, RestDtos
    └── out/
        ├── ai/copilot/   CopilotLlmAdapter, CopilotEmbeddingAdapter
        ├── parser/       PdfParserAdapter, CsvParserAdapter
        ├── storage/      LocalDocumentStorageAdapter
        └── vectorstore/  PgVectorStoreAdapter
```
