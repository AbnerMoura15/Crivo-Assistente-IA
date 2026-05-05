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
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "Quais são os procedimentos de segurança?", "history": []}'
```

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
