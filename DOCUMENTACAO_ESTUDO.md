# Documentação de Estudo — Crivo AI (RAGBot)

> **Para quem é este documento?**  
> Para desenvolvedores iniciantes que conhecem Java 8, SQL Server, um pouco de Spring e um pouco de JUnit/Mockito.  
> Cada seção explica **o que é**, **por que foi feito assim**, **o que a documentação oficial diz** e **um exemplo em outro contexto**.

---

## Sumário

1. [O que é RAG?](#1-o-que-é-rag)
2. [Arquitetura Hexagonal](#2-arquitetura-hexagonal)
3. [Stack Tecnológica — pom.xml](#3-stack-tecnológica--pomxml)
4. [Configuração — application.yml e Docker](#4-configuração--applicationyml-e-docker)
5. [Banco de Dados — Flyway Migrations](#5-banco-de-dados--flyway-migrations)
6. [pgvector — Busca Vetorial no PostgreSQL](#6-pgvector--busca-vetorial-no-postgresql)
7. [Segurança — JWT e Spring Security](#7-segurança--jwt-e-spring-security)
8. [Filtros HTTP — Request ID e Rate Limit](#8-filtros-http--request-id-e-rate-limit)
9. [Entidades de Domínio](#9-entidades-de-domínio)
10. [Pipeline de Ingestão de Documentos](#10-pipeline-de-ingestão-de-documentos)
11. [Pipeline de Chat — O Coração do RAG](#11-pipeline-de-chat--o-coração-do-rag)
12. [Adaptadores de IA — Groq e Google](#12-adaptadores-de-ia--groq-e-google)
13. [Serviços de Suporte](#13-serviços-de-suporte)
14. [Controllers REST](#14-controllers-rest)
15. [Como Converter Este Arquivo em PDF](#15-como-converter-este-arquivo-em-pdf)

---

## 1. O que é RAG?

**RAG** significa **Retrieval-Augmented Generation** (Geração Aumentada por Recuperação).

Imagine que você quer que um chatbot de IA responda perguntas sobre os documentos internos da sua empresa — manuais, contratos, procedimentos. O problema é que modelos de IA como o ChatGPT não conhecem esses documentos.

A solução RAG funciona assim:

```
1. INGESTÃO (pré-processamento):
   Documento PDF/CSV
       → Extrai texto
       → Divide em pedaços (chunks)
       → Converte cada pedaço em um "vetor" numérico (embedding)
       → Salva no banco de dados

2. CONSULTA (em tempo real):
   Pergunta do usuário
       → Converte a pergunta em vetor
       → Busca os pedaços mais parecidos no banco (busca vetorial)
       → Monta um contexto com esses pedaços
       → Envia para o modelo de IA (Groq/LLM)
       → Retorna a resposta ao usuário
```

**Por que não simplesmente enviar o documento inteiro para a IA?**  
Porque os modelos de IA têm um limite de texto que conseguem processar de uma vez (chamado de "context window"). Documentos grandes não cabem nesse limite.

---

## 2. Arquitetura Hexagonal

O projeto usa **Arquitetura Hexagonal** (também chamada de "Ports and Adapters").

### A ideia central

O núcleo da aplicação (regras de negócio) **não conhece** tecnologias externas como banco de dados, APIs ou frameworks. Ele só fala através de **interfaces** (chamadas de "ports").

```
┌─────────────────────────────────────────────────────────┐
│                    NÚCLEO DA APLICAÇÃO                  │
│                                                         │
│   domain/          ← Entidades puras (sem anotações)   │
│   application/     ← Use cases e serviços              │
│     port/in/       ← Interfaces que o mundo chama      │
│     port/out/      ← Interfaces que o core precisa     │
│     service/       ← Implementações das regras         │
│                                                         │
└──────────┬──────────────────────────────┬───────────────┘
           │                              │
    ADAPTADORES DE                  ADAPTADORES DE
    ENTRADA (in/)                   SAÍDA (out/)
           │                              │
    Controllers REST              Banco de dados
    (quem chama o core)           APIs de IA
                                  Parsers de arquivo
```

### Por que esse padrão?

**Problema que resolve:** Se você mistura código de negócio com código de banco de dados, fica impossível trocar o banco sem reescrever tudo. E impossível testar sem um banco real.

**Benefício concreto no RAGBot:** Para trocar o provedor de IA de Groq para OpenAI, basta criar um novo `Adapter` que implementa `LlmPort`. O resto do código **não muda nada**.

**Referência oficial:** [Hexagonal Architecture por Alistair Cockburn](https://alistair.cockburn.us/hexagonal-architecture/)

### Exemplo em outro contexto

Um sistema de e-commerce poderia ter:
- `port/out/PaymentPort` (interface)
- `adapter/out/PaypalPaymentAdapter` (implementação para PayPal)
- `adapter/out/StripePaymentAdapter` (implementação para Stripe)

Para trocar de PayPal para Stripe? Só mudar uma configuração.

---

## 3. Stack Tecnológica — pom.xml

O arquivo `pom.xml` é o "cardápio" do projeto Maven. Ele lista todas as dependências (bibliotecas externas) que o projeto usa.

### Diferença de Java 8 para Java 21

Você conhece Java 8. Este projeto usa **Java 21**. As principais diferenças que você vai ver no código:

| Java 8 | Java 21 | O que é |
|--------|---------|---------|
| `class` normal | `record` | Classe imutável com campos, getters e `equals`/`hashCode` automáticos |
| `if-else` | `switch` como expressão | Switch mais poderoso que retorna valor |
| Strings normais | Text blocks `"""..."""` | Strings multilinha sem escapes |
| `Optional.get()` | `Optional.orElseThrow()` | Mais seguro para lidar com valores ausentes |

**O que é um `record`?**

```java
// Java 8: você escreveria assim
public class Chunk {
    private final String id;
    private final String content;
    
    public Chunk(String id, String content) {
        this.id = id;
        this.content = content;
    }
    
    public String getId() { return id; }
    public String getContent() { return content; }
    // + equals, hashCode, toString...
}

// Java 21: equivalente compacto
public record Chunk(String id, String content) {}
// Acesso: chunk.id(), chunk.content() (sem "get")
```

### Principais dependências explicadas

#### `spring-boot-starter-web`
Inclui o Spring MVC (controllers REST) e o servidor Tomcat embutido. Você não precisa configurar um servidor separado.

**Referência:** [Spring Web MVC](https://docs.spring.io/spring-framework/reference/web/webmvc.html)

#### `spring-boot-starter-security`
Segurança para a aplicação: autenticação, autorização, proteção contra ataques.

**Referência:** [Spring Security](https://docs.spring.io/spring-security/reference/)

#### `spring-boot-starter-jdbc`
Acesso ao banco de dados com `JdbcTemplate`. É a versão simplificada do JDBC — sem precisar abrir conexões manualmente.

**Referência:** [Spring JDBC](https://docs.spring.io/spring-framework/reference/data-access/jdbc.html)

#### `spring-boot-starter-webflux`
Permite fazer chamadas HTTP de forma reativa (não-bloqueante). Usado para chamar as APIs externas (Groq, Google) com timeout e retry automáticos.

**Por que não usar `RestTemplate` simples?**  
`RestTemplate` é bloqueante — a thread fica parada esperando a resposta da API. Com `WebClient` (do WebFlux), a thread é liberada durante a espera e pode fazer outras coisas. Além disso, o `WebClient` suporta timeout e retry nativamente.

**Referência:** [WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

#### `flyway-core`
Controla as migrações do banco de dados (como o Liquibase). Em vez de rodar scripts SQL manualmente, o Flyway os aplica automaticamente na ordem certa.

**Referência:** [Flyway](https://flywaydb.org/documentation/)

#### `pdfbox`
Extrai texto de arquivos PDF.

**Referência:** [Apache PDFBox](https://pdfbox.apache.org/)

#### `opencsv`
Lê e escreve arquivos CSV.

**Referência:** [OpenCSV](http://opencsv.sourceforge.net/)

#### `jjwt-api` (e impl + jackson)
Gera e valida tokens JWT (JSON Web Token) para autenticação.

**Referência:** [JJWT](https://github.com/jwtk/jjwt)

#### `testcontainers`
Permite rodar um PostgreSQL real (em Docker) durante os testes, sem precisar de um banco instalado.

**Referência:** [Testcontainers](https://testcontainers.com/guides/testing-spring-boot-rest-api-using-testcontainers/)

#### `jacoco-maven-plugin`
Mede a cobertura de código pelos testes. O projeto exige mínimo de **80%** de cobertura — se os testes cobrirem menos, o build falha.

**Referência:** [JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/)

---

## 4. Configuração — application.yml e Docker

### Por que YAML em vez de .properties?

Você pode conhecer o formato `.properties` (chave=valor). O `.yml` (YAML) é hierárquico — mais legível para configurações complexas.

```yaml
# .properties (formato antigo):
spring.datasource.url=jdbc:postgresql://localhost:5544/ragbot
spring.datasource.username=postgres

# .yml (formato atual):
spring:
  datasource:
    url: jdbc:postgresql://localhost:5544/ragbot
    username: postgres
```

### Variáveis de ambiente no YAML

```yaml
groq:
  token: ${GROQ_API_KEY}                    # obrigatório — falha se não definido
  model: ${GROQ_MODEL:llama-3.3-70b-versatile}  # opcional — usa default se não definido
```

**Sintaxe:** `${NOME_VARIAVEL:valor_padrão}`

**Por que isso é importante?**  
Credenciais (API keys, senhas) nunca devem ser commitadas no Git. Elas ficam em variáveis de ambiente, que são definidas no servidor ou no arquivo `.env` (não versionado).

**Exemplo em outro contexto:** Um sistema bancário teria `${DB_PASSWORD}` para a senha do banco — nunca no código-fonte.

### HikariCP — Pool de Conexões

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 2
      minimum-idle: 1
      connection-timeout: 30000   # 30 segundos
```

**O que é um pool de conexões?**  
Abrir uma conexão com o banco de dados é caro (leva tempo). O pool mantém um conjunto de conexões abertas e as reutiliza. Quando uma requisição precisa do banco, ela "pega emprestado" uma conexão do pool e devolve quando termina.

**Por que apenas 2 conexões?**  
O banco usado em produção (Supabase) tem um limite pequeno no plano gratuito. Em produção enterprise você aumentaria esse número.

**Referência:** [HikariCP](https://github.com/brettwooldridge/HikariCP)

### Docker Compose — Ambiente de Desenvolvimento

```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    ports:
      - "5544:5432"   # porta local:porta no container
    environment:
      POSTGRES_DB: ragbot
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U postgres"]
      interval: 10s
      retries: 5
```

**Por que usar Docker para o banco?**  
Evita instalar o PostgreSQL na máquina. Qualquer desenvolvedor do time roda `docker compose up -d` e tem o banco funcionando em segundos, com a mesma versão e configuração.

**O que é healthcheck?**  
O Docker verifica periodicamente se o banco está saudável (`pg_isready`). Outros serviços que dependem do banco (como a aplicação Java) só sobem depois que o healthcheck passar.

**Referência:** [Docker Compose Healthcheck](https://docs.docker.com/compose/compose-file/05-services/#healthcheck)

**Exemplo em outro contexto:** Um sistema de RH poderia usar `docker-compose.yml` com PostgreSQL + Redis para testes locais, garantindo que todos os desenvolvedores usam as mesmas versões.

### Dockerfile — Build em Múltiplos Estágios

```dockerfile
# Estágio 1: Compila o projeto
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q   # baixa dependências (cacheado)
COPY src/ src/
RUN mvn package -DskipTests -q     # compila sem rodar testes

# Estágio 2: Apenas o necessário para rodar
FROM eclipse-temurin:21-jre-alpine AS runtime
# Usuário não-root por segurança
RUN addgroup -S ragbot && adduser -S ragbot -G ragbot
USER ragbot
EXPOSE 8080
```

**Por que dois estágios?**  
O Maven + JDK ocupa ~500 MB. A imagem final só precisa do JRE (~200 MB). Com dois estágios, a imagem de produção é bem menor e não contém ferramentas de compilação (que poderiam ser exploradas por invasores).

**Por que usuário não-root?**  
Se um invasor explorar uma vulnerabilidade na aplicação, ele terá acesso apenas ao usuário `ragbot`, não ao `root`. Isso limita o dano.

**Cache de camadas do Docker:**  
O `COPY pom.xml` vem antes do `COPY src/` porque o pom.xml muda raramente. O Docker cacheia as camadas — se o `pom.xml` não mudou, ele não re-baixa as dependências Maven.

**Referência:** [Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)

---

## 5. Banco de Dados — Flyway Migrations

### O que é Flyway?

Flyway é um gerenciador de migrações de banco de dados. Em vez de rodar scripts SQL manualmente (e esquecer qual foi aplicado onde), o Flyway os aplica automaticamente, na ordem certa, e registra quais já foram executados.

**Analogia:** É como o histórico de commits do Git, mas para o banco de dados.

**Diferença do SQL Server:** No SQL Server, você pode usar ferramentas como SSDT ou scripts manuais. O Flyway automatiza isso e é compatível com vários bancos (PostgreSQL, MySQL, Oracle, SQL Server).

### Convenção de nomenclatura

```
V1__create_tables.sql
V2__upgrade_embedding_dimension_to_3072.sql
V3__create_users.sql
...
V15__max_history.sql
```

**Formato:** `V{número}__{descrição}.sql`  
O número define a ordem. O Flyway aplica do menor para o maior, apenas os que ainda não foram aplicados.

### V1 — Tabelas Base

```sql
-- Habilita a extensão pgvector (busca vetorial)
CREATE EXTENSION IF NOT EXISTS vector;

-- Tabela de documentos indexados
CREATE TABLE documents (
    id VARCHAR(36) PRIMARY KEY,          -- UUID como string
    file_name VARCHAR(512) NOT NULL,
    mime_type VARCHAR(128) NOT NULL,     -- ex: "application/pdf"
    storage_reference VARCHAR(1024) NOT NULL, -- caminho do arquivo em disco
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    ingested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Tabela de chunks (pedaços de texto) com seus vetores
CREATE TABLE chunks (
    id VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    content TEXT NOT NULL,               -- texto do chunk
    embedding vector(1536),              -- vetor numérico (atualizado para 3072 em V2)
    metadata JSONB NOT NULL DEFAULT '{}' -- dados extras em JSON
);

-- Índice especial para busca por similaridade vetorial
CREATE INDEX idx_chunks_embedding ON chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

**O que é `ON DELETE CASCADE`?**  
Se um documento for deletado da tabela `documents`, todos os chunks relacionados são deletados automaticamente. Sem isso, você teria chunks "órfãos" sem documento pai.

**O que é `JSONB`?**  
É um tipo PostgreSQL que armazena JSON de forma binária (indexável e pesquisável). Diferente do SQL Server onde você guardaria em `NVARCHAR(MAX)`, o JSONB permite queries dentro do JSON.

**O que é `IVFFLAT`?**  
É um algoritmo de indexação para vetores de alta dimensão. Sem ele, cada busca compararia o vetor da pergunta com todos os vetores no banco (O(n)). Com IVFFLAT, a busca é aproximada mas muito mais rápida.

**Referência:** [pgvector IVFFLAT](https://github.com/pgvector/pgvector#indexing)

### V3 — Tabela de Usuários

```sql
CREATE TABLE users (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(100) NOT NULL UNIQUE,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,   -- BCrypt, NUNCA texto puro
    role          VARCHAR(20)  NOT NULL DEFAULT 'VIEWER'
                  CHECK (role IN ('VIEWER','EDITOR','ADMIN')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login    TIMESTAMPTZ
);

-- Usuário admin inicial: senha Admin@123
INSERT INTO users (id, username, email, password_hash, role, active)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'admin',
    'admin@ragbot.local',
    '$2a$12$/WCz2rD9qXs0kh7gKiVoFe7he4jKdRxcgBBOxnLIBR1mQjXp15Ru2',
    'ADMIN',
    TRUE
);
```

**Por que `password_hash` e não `password`?**  
Senhas **jamais** devem ser gravadas em texto puro. O BCrypt transforma `Admin@123` em `$2a$12$...` de forma irreversível. Para verificar a senha no login, o sistema faz o BCrypt da senha digitada e compara com o hash — nunca decifra o hash.

**O que é `CHECK (role IN (...))`?**  
É uma constraint do banco — o banco rejeita qualquer inserção com um role inválido. Isso garante a integridade dos dados mesmo se o código Java tiver um bug.

**O que é `TIMESTAMPTZ`?**  
É `TIMESTAMP WITH TIME ZONE`. Armazena data/hora com informação de fuso horário. Use sempre `TIMESTAMPTZ` em vez de `TIMESTAMP` para evitar bugs de fuso horário em sistemas distribuídos.

**Referência:** [PostgreSQL Date/Time Types](https://www.postgresql.org/docs/current/datatype-datetime.html)

### Resumo das Migrations

| Versão | O que faz |
|--------|-----------|
| V1 | Cria `documents` e `chunks` com índice vetorial IVFFLAT |
| V2 | Aumenta dimensão do embedding de 1536 para 3072 (mudança de provedor) |
| V3 | Cria `users` com BCrypt e insere admin padrão |
| V4 | Adiciona coluna `status` em `documents` e `chunks` (soft delete) |
| V5 | Cria `rag_interactions` para observabilidade (quem perguntou o quê) |
| V6 | Cria `audit_log` para rastreabilidade de ações |
| V7 | Cria `chat_sessions` e `chat_messages` para histórico de conversas |
| V8 | Cria `user_token_usage` para controle de quota mensal |
| V9 | Cria `rate_limit_counters` para controle de requisições |
| V10 | Adiciona índice de busca por texto completo (BM25/FTS) em `chunks` |
| V11 | Cria `namespaces` e adiciona `namespace_id` em `documents` |
| V12 | Adiciona `version_number` e `original_document_id` para versionamento |
| V13 | Cria `rag_config` (configuração dinâmica via admin UI) |
| V14 | Adiciona `processing_status` em `documents` (PROCESSING/READY/FAILED) |
| V15 | Insere seed de `chat.max-history-messages=6` na config |

---

## 6. pgvector — Busca Vetorial no PostgreSQL

### O que é um embedding (vetor)?

Imagine que cada texto pode ser representado como um ponto no espaço. Textos com significados parecidos ficam próximos; textos com significados diferentes ficam distantes.

Um modelo de IA (como o Gemini da Google) converte texto em uma lista de números (vetor) com 3072 dimensões:

```
"Procedimento de segurança" → [0.123, -0.456, 0.789, ..., 0.321]  (3072 números)
"Regras de segurança"       → [0.124, -0.451, 0.791, ..., 0.318]  (muito parecido!)
"Receita de bolo"           → [-0.832, 0.211, -0.654, ..., 0.099]  (bem diferente)
```

### Distância de Cosseno

A busca usa a **distância de cosseno** entre vetores:

- Distância 0 = idênticos
- Distância 1 = completamente diferentes

No SQL: `c.embedding <=> ?::vector` calcula a distância de cosseno.  
O score de similaridade é: `score = 1 - distância`

### Busca Vetorial Pura — `findSimilar`

```java
// PgVectorStoreAdapter.java
public List<ScoredChunk> findSimilar(List<Float> queryEmbedding, int topK, String namespaceId) {
    String vectorStr = toVectorString(queryEmbedding);  // "[0.1, -0.5, 0.3, ...]"
    return jdbc.query(
            """
            SELECT c.id, c.document_id, c.content, c.embedding::text, c.metadata::text,
                   1.0 - (c.embedding <=> ?::vector) AS score   -- similaridade
            FROM chunks c
            JOIN documents d ON c.document_id = d.id
            WHERE c.status = 'ACTIVE'
              AND d.status = 'ACTIVE'
              AND d.namespace_id = ?                             -- isolamento por namespace
            ORDER BY c.embedding <=> ?::vector                  -- menor distância = mais relevante
            LIMIT ?
            """,
            this::mapScoredRow,
            vectorStr, namespaceId, vectorStr, topK
    );
}
```

**Explicação linha a linha:**

- `1.0 - (c.embedding <=> ?::vector)` — `<=>` é o operador de distância de cosseno do pgvector. Subtraímos de 1 para virar score de *similaridade* (maior = melhor).
- `?::vector` — cast de string para o tipo `vector` do pgvector.
- `JOIN documents d ON c.document_id = d.id` — garante que só buscamos chunks de documentos no namespace correto.
- `ORDER BY c.embedding <=> ?::vector` — ordena pelos mais próximos (menor distância).
- `LIMIT ?` — retorna apenas os `topK` mais relevantes.

**Referência:** [pgvector operators](https://github.com/pgvector/pgvector#vector-operators)

### Busca Híbrida — `findSimilarHybrid`

```java
// Score combinado: 85% vetorial + 15% BM25 (busca por palavras-chave)
String sql = """
    SELECT c.id, c.document_id, c.content, c.embedding::text, c.metadata::text,
           (? * (1.0 - (c.embedding <=> ?::vector))              -- parte vetorial
            + ? * COALESCE(ts_rank(to_tsvector('simple', c.content),
                           plainto_tsquery('simple', ?)), 0.0))  -- parte BM25
           AS score
    FROM chunks c
    JOIN documents d ON c.document_id = d.id
    WHERE c.status = 'ACTIVE'
      AND d.status = 'ACTIVE'
      AND d.namespace_id = ?
    ORDER BY (...) DESC
    LIMIT ?
    """;
```

**O que é BM25?**  
BM25 (Best Match 25) é o algoritmo clássico de busca por palavras-chave (usado pelo Google e Elasticsearch internamente). Ele pontua documentos pela frequência das palavras-chave, ajustando pelo comprimento do documento.

**`to_tsvector('simple', c.content)`** — converte o texto em um vetor de busca textual. `'simple'` usa tokenização básica (universal, sem depender de dicionário de idioma).

**`plainto_tsquery('simple', ?)`** — converte a pergunta do usuário em uma query de busca textual.

**`ts_rank(...)`** — retorna o score BM25 (geralmente entre 0.01 e 0.05).

**Por que combinar vetorial + BM25?**  
- Busca vetorial é boa para significado semântico ("qual é o prazo?" encontra "o tempo máximo é de 30 dias")
- BM25 é bom para palavras exatas ("CPF" encontra "CPF" mesmo sem semelhança semântica)
- A combinação é mais robusta do que qualquer uma sozinha.

**Referência:** [PostgreSQL Full Text Search](https://www.postgresql.org/docs/current/textsearch.html)

### `toVectorString` — Convertendo Lista para SQL

```java
private String toVectorString(List<Float> embedding) {
    return "[" + embedding.stream()
            .map(String::valueOf)               // 0.123f → "0.123"
            .collect(Collectors.joining(","))   // junta com vírgula
            + "]";
}
// Resultado: "[0.123,-0.456,0.789,...]"
```

O pgvector aceita vetores no formato `[n1,n2,n3,...]`. Como o Java não tem um tipo nativo para isso, precisamos converter a `List<Float>` para esta string.

### `mapScoredRow` — Lendo Resultado do Banco

```java
private ScoredChunk mapScoredRow(ResultSet rs, int rowNum) throws SQLException {
    Chunk chunk = new Chunk(
            rs.getString("id"),
            rs.getString("document_id"),
            rs.getString("content"),
            parseEmbedding(rs.getString("embedding")),  // "[0.1,-0.5,...]" → List<Float>
            parseMetadata(rs.getString("metadata"))      // JSON → Map<String, String>
    );
    double score = rs.getDouble("score");
    return new ScoredChunk(chunk, score);
}
```

**O que é `ResultSet`?**  
É a interface JDBC que representa o resultado de uma query SQL. Você navega linha por linha com `rs.next()` (o JdbcTemplate faz isso automaticamente) e lê cada coluna com `rs.getString("coluna")`.

**Diferença do SQL Server:** No SQL Server você usaria `SqlDataReader` (C#) ou o mesmo `ResultSet` (Java com driver MSSQL). A interface é a mesma; só muda o driver JDBC.

**Referência:** [JdbcTemplate query](https://docs.spring.io/spring-framework/reference/data-access/jdbc/core.html#jdbc-JdbcTemplate-examples-query)

---

## 7. Segurança — JWT e Spring Security

### O que é JWT?

JWT (JSON Web Token) é um token de autenticação que funciona assim:

1. Usuário faz login com username + senha
2. Servidor verifica e gera um token JWT assinado
3. Usuário envia o token em cada requisição (`Authorization: Bearer <token>`)
4. Servidor valida o token sem consultar o banco — tudo está no token

**Estrutura de um JWT:**
```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbiIsInJvbGUiOiJBRE1JTiIsImlhdCI6...}
     HEADER                    PAYLOAD (usuário, role, expiração)
```

O token tem três partes separadas por `.`, encodadas em Base64. A terceira parte é a **assinatura** — garante que ninguém alterou o conteúdo.

**Por que JWT em vez de sessão?**  
Sessão guarda estado no servidor. JWT é **stateless** — o servidor não precisa guardar nada; tudo está no token. Isso permite escalar horizontalmente (múltiplas instâncias do servidor).

### JwtTokenProvider — Gerar e Validar Tokens

```java
// config/security/JwtTokenProvider.java
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${ragbot.security.jwt.secret}") String secret,
            @Value("${ragbot.security.jwt.expiration-seconds:86400}") long expirationSeconds) {
        // Converte a string do application.yml em uma chave criptográfica
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationSeconds = expirationSeconds;  // 86400s = 24h
    }
```

**`@Value("${...}")`** — injeta o valor do `application.yml`. O Spring lê a configuração e passa direto para o construtor.

**`Keys.hmacShaKeyFor(...)`** — cria uma chave HMAC-SHA256 a partir da string. O algoritmo HS256 garante que apenas quem tem o segredo consegue criar ou verificar tokens.

```java
    public String generateToken(String username, String role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationSeconds * 1000L);

        return Jwts.builder()
                .subject(username)           // quem é o usuário
                .claim("role", role)         // qual o perfil (VIEWER/EDITOR/ADMIN)
                .issuedAt(now)              // quando foi emitido
                .expiration(expiry)         // quando expira
                .signWith(secretKey)        // assina com o segredo
                .compact();                 // serializa para string
    }
```

**`Jwts.builder()`** — API fluente da biblioteca JJWT para construir tokens.

**`* 1000L`** — `expirationSeconds` é em segundos; `Date` usa milissegundos. O `L` evita overflow de `int`.

```java
    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            parseClaims(token);   // lança exceção se inválido ou expirado
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token JWT inválido: {}", e.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)          // verifica a assinatura
                .build()
                .parseSignedClaims(token)       // decodifica e valida
                .getPayload();                  // retorna os dados
    }
```

**`JwtException`** — exceção lançada quando o token é inválido, foi adulterado ou expirou. Capturamos para retornar `false` em vez de lançar exceção para cima.

**Referência:** [JJWT](https://github.com/jwtk/jjwt#reading-a-jwt)

**Exemplo em outro contexto:** Um app mobile de banco usaria o mesmo padrão — o app guarda o JWT após o login e o envia em toda chamada à API.

### SecurityConfig — Regras de Acesso

```java
// config/security/SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity   // habilita @PreAuthorize nos métodos
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // APIs REST não usam CSRF (formulários HTML usariam)
            .csrf(AbstractHttpConfigurer::disable)

            // Stateless: sem HttpSession — o JWT é o estado
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            .authorizeHttpRequests(auth -> auth
                // Qualquer um pode chamar login e registro
                .requestMatchers("/api/auth/**").permitAll()
                // Arquivos estáticos (frontend)
                .requestMatchers("/", "/index.html", "/**.css", "/**.js").permitAll()

                // Leitura de documentos: qualquer papel autenticado
                .requestMatchers(HttpMethod.GET, "/api/documents/**")
                    .hasAnyRole("VIEWER", "EDITOR", "ADMIN")
                // Upload: só EDITOR e ADMIN
                .requestMatchers(HttpMethod.POST, "/api/documents/**")
                    .hasAnyRole("EDITOR", "ADMIN")
                // Deletar: só ADMIN
                .requestMatchers(HttpMethod.DELETE, "/api/documents/**")
                    .hasRole("ADMIN")

                // Qualquer outra rota: autenticado (qualquer papel)
                .anyRequest().authenticated()
            )

            // Adiciona o filtro JWT antes do filtro padrão de login
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)

            // Retorna 401 (Unauthorized) em vez de 403 (Forbidden) para não autenticados
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, exc) ->
                    res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized"))
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);  // custo 12: bom equilíbrio segurança/performance
    }
}
```

**`@Configuration`** — marca a classe como fonte de beans Spring. Equivale a um arquivo XML de configuração do Spring antigo.

**`@Bean`** — o método retorna um objeto que o Spring vai gerenciar. Quando outro componente precisar de `PasswordEncoder`, o Spring injeta automaticamente o bean criado aqui.

**`SessionCreationPolicy.STATELESS`** — o Spring não cria `HttpSession`. Cada requisição deve se autenticar pelo JWT.

**`hasAnyRole("VIEWER", ...)`** — o Spring adiciona o prefixo `ROLE_` automaticamente. `hasAnyRole("VIEWER")` verifica a authority `ROLE_VIEWER`.

**`BCryptPasswordEncoder(12)`** — o número 12 é o "custo" do algoritmo. Custo 12 = ~250ms para gerar um hash. Muito rápido para o usuário, mas muito lento para um atacante que tenta milhões de senhas.

**401 vs 403:**
- **401 Unauthorized** — "Não sei quem você é" (não autenticado)
- **403 Forbidden** — "Sei quem você é, mas você não pode fazer isso" (não autorizado)

**Referência:** [Spring Security Authorization](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)

**Exemplo em outro contexto:** Um sistema de hospital teria:
- `permitAll()` para a tela de login
- `hasRole("MEDICO")` para prontuários
- `hasRole("ADMIN")` para gestão de usuários

### AuthService — Login e Registro

```java
// application/service/AuthService.java
@Override
public String login(String username, String password) {
    // 1. Busca o usuário no banco
    User user = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                // Registra tentativa falha no audit log
                auditLogService.record(username, AuditAction.LOGIN_FAILED, "Usuário não encontrado");
                return new InvalidCredentialsException();  // mensagem genérica (segurança)
            });

    // 2. Verifica se está ativo
    if (!user.active()) {
        auditLogService.record(username, AuditAction.LOGIN_FAILED, "Usuário inativo");
        throw new InvalidCredentialsException("Usuário inativo. Contate o administrador.");
    }

    // 3. Verifica senha com BCrypt
    if (!passwordEncoder.matches(password, user.passwordHash())) {
        auditLogService.record(username, AuditAction.LOGIN_FAILED, "Senha incorreta");
        throw new InvalidCredentialsException();
    }

    // 4. Atualiza last_login e registra sucesso
    userRepository.updateLastLogin(user.id());
    auditLogService.record(username, AuditAction.LOGIN_SUCCESS, "role=" + user.role().name());

    // 5. Gera e retorna o JWT
    return jwtTokenProvider.generateToken(user.username(), user.role().name());
}
```

**`Optional.orElseThrow()`** — se `findByUsername` retornar vazio (usuário não existe), lança a exceção. É a alternativa segura ao `Optional.get()` que lança `NoSuchElementException` sem mensagem.

**`passwordEncoder.matches(raw, hash)`** — computa o BCrypt da senha digitada e compara com o hash salvo. **Nunca** decifra o hash.

**Por que lançar `InvalidCredentialsException` para usuário não encontrado?**  
Segurança: se você retornar "usuário não encontrado", um atacante sabe que o username não existe e pode parar de tentar aquele. Retornar a mesma mensagem para erros de senha e de usuário evita esse vazamento de informação.

**Referência:** [Spring Security Password Encoding](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)

---

## 8. Filtros HTTP — Request ID e Rate Limit

### O que é um filtro?

Um filtro (`Filter`) é um componente que intercepta toda requisição HTTP **antes** de chegar ao controller e toda resposta **depois** de sair do controller. É como um pedágio — toda requisição passa por ele.

O Spring Boot usa `OncePerRequestFilter` para garantir que o filtro execute exatamente uma vez por requisição (mesmo em encaminhamentos internos).

### RequestIdFilter — Rastreabilidade

```java
// config/filter/RequestIdFilter.java
@Component
@Order(1)  // Primeiro filtro a executar
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Aceita Request ID do cliente ou gera um novo
        String requestId = request.getHeader("X-Request-ID");
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();  // ex: "550e8400-e29b-41d4-a716-446655440000"
        }

        // Injeta no MDC (Mapped Diagnostic Context) do SLF4J
        MDC.put("requestId", requestId);
        // Retorna ao cliente para ele poder referenciar
        response.setHeader("X-Request-ID", requestId);

        try {
            chain.doFilter(request, response);  // passa para o próximo filtro/controller
        } finally {
            MDC.remove("requestId");  // OBRIGATÓRIO limpar — threads são reutilizadas
        }
    }
}
```

**O que é MDC?**  
MDC (Mapped Diagnostic Context) é um mapa de contexto do SLF4J que é automaticamente incluído em cada linha de log. Uma vez que você faz `MDC.put("requestId", id)`, todos os logs daquela thread automaticamente incluem `requestId`.

Resultado em produção: em vez de logs impossíveis de correlacionar:
```
ERROR Failed to process document
ERROR Database connection timeout
```

Você tem:
```
ERROR [requestId=abc-123] Failed to process document
ERROR [requestId=abc-123] Database connection timeout
```

Agora você sabe que esses dois erros vieram da mesma requisição.

**`chain.doFilter(request, response)`** — passa a requisição para o próximo filtro na cadeia. Sem isso, a requisição nunca chegaria ao controller.

**Por que `finally { MDC.remove(...) }`?**  
Threads são reutilizadas em um pool. Se você não limpar o MDC, a próxima requisição na mesma thread herdaria o `requestId` da requisição anterior — um bug difícil de detectar.

**Referência:** [SLF4J MDC](https://www.slf4j.org/manual.html#mdc)

### RateLimitFilter — Controle de Requisições

```java
// config/filter/RateLimitFilter.java
@Component
@Order(2)  // Segundo filtro — depois do RequestIdFilter
public class RateLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // Endpoints isentos (login, arquivos estáticos)
        if (isExempt(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Determina a chave: usuário autenticado ou IP anônimo
        String key = resolveKey(request);

        // Verifica se pode prosseguir
        if (!rateLimiterService.tryAcquire(key)) {
            sendTooManyRequests(response, key);
            return;  // NÃO chama chain.doFilter — bloqueia a requisição
        }

        chain.doFilter(request, response);
    }

    private String resolveKey(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return "user:" + auth.getName();  // ex: "user:joao"
        }
        return "ip:" + extractClientIp(request);  // ex: "ip:192.168.1.10"
    }

    private String extractClientIp(HttpServletRequest request) {
        // X-Forwarded-For: quando há proxy (Nginx, Load Balancer) na frente
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();  // primeiro IP = cliente original
        }
        return request.getRemoteAddr();
    }
}
```

**`SecurityContextHolder.getContext().getAuthentication()`** — acessa o usuário autenticado da requisição atual. O filtro JWT popula isso antes do RateLimitFilter.

**Por que `"anonymousUser".equals(auth.getPrincipal())`?**  
O Spring popula o `SecurityContext` com um `AnonymousAuthenticationToken` para requisições não autenticadas. O principal desse token é a string `"anonymousUser"`.

**`X-Forwarded-For`** — quando há um proxy (Nginx) na frente, o IP real do cliente chega nesse header. Sem verificar esse header, você limitaria pelo IP do proxy, não do cliente.

### RateLimiterService — A Lógica do Limite

```java
// application/service/RateLimiterService.java
@Service
public class RateLimiterService {

    // ConcurrentHashMap é thread-safe: múltiplas threads podem acessar simultaneamente
    private final ConcurrentHashMap<String, AtomicInteger> minuteCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicInteger> dayCounters    = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        boolean isAnon = key.startsWith("ip:");
        int limitMin = isAnon ? anonPerMinute : userPerMinute;  // anon: 5/min, user: 20/min
        int limitDay = isAnon ? anonPerDay    : userPerDay;     // anon: 50/dia, user: 500/dia

        // computeIfAbsent: cria AtomicInteger(0) se a chave não existir
        // incrementAndGet: incrementa atomicamente e retorna o novo valor
        int minuteCount = minuteCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (minuteCount > limitMin) {
            minuteCounters.get(key).decrementAndGet();  // desfaz o incremento
            return false;
        }

        int dayCount = dayCounters
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (dayCount > limitDay) {
            minuteCounters.get(key).decrementAndGet();  // desfaz ambos
            dayCounters.get(key).decrementAndGet();
            return false;
        }

        return true;
    }

    @Scheduled(fixedDelay = 60_000)   // a cada 60 segundos
    public void resetMinuteCounters() {
        minuteCounters.clear();
    }

    @Scheduled(cron = "0 0 0 * * *")  // todo dia à meia-noite
    public void resetDayCounters() {
        dayCounters.clear();
    }
}
```

**`ConcurrentHashMap`** — mapa thread-safe. Em Java 8 você usaria `Collections.synchronizedMap()` ou `Hashtable`. O `ConcurrentHashMap` é mais eficiente pois não bloqueia o mapa inteiro — apenas o "bucket" sendo acessado.

**`AtomicInteger`** — inteiro que garante operações atômicas (incrementar e ler em uma operação, sem race condition). Em Java 8, sem isso, duas threads poderiam incrementar o contador ao mesmo tempo e perder um incremento.

**`@Scheduled(fixedDelay = 60_000)`** — executa o método a cada 60 segundos (60.000 ms). Requer `@EnableScheduling` na classe principal.

**`@Scheduled(cron = "0 0 0 * * *")`** — expressão cron: segundo=0, minuto=0, hora=0, todo dia, todo mês, todo dia da semana = meia-noite.

**Referência:** [Spring Scheduling](https://docs.spring.io/spring-framework/reference/integration/scheduling.html)

**Exemplo em outro contexto:** Um sistema de e-commerce limitaria chamadas à API de CEP (dos Correios) a 1000/hora para não ultrapassar a cota gratuita.

---

## 9. Entidades de Domínio

As entidades de domínio são **records Java** — classes imutáveis sem anotações de framework. Isso garante que o domínio seja independente do Spring, JPA ou qualquer tecnologia.

### Document

```java
public record Document(
        String id,                 // UUID
        String fileName,           // "manual_segurança.pdf"
        String mimeType,           // "application/pdf"
        String storageReference,   // caminho em disco
        long fileSizeBytes,
        Instant ingestedAt,        // quando foi indexado
        String namespaceId,        // qual namespace pertence
        int version,               // número da versão (1, 2, 3...)
        String originalDocumentId, // ID da primeira versão (para versionamento)
        String processingStatus    // "PROCESSING" | "READY" | "FAILED"
) {}
```

**`Instant`** — representa um momento no tempo, fuso UTC. É a forma correta de guardar timestamps no Java moderno. Diferente de `Date` (Java 8), `Instant` é imutável e sem bugs de fuso.

### ScoredChunk — Re-ranking

```java
// domain/entity/ScoredChunk.java
public record ScoredChunk(Chunk chunk, double score) {

    /**
     * Score ajustado: penaliza chunks muito curtos.
     * Chunks curtos tendem a ser fragmentos sem contexto.
     */
    public double adjustedScore(int minLength) {
        if (chunk.content().length() < minLength) {
            return score * 0.85;   // penalidade de 15%
        }
        return score;
    }
}
```

**Por que penalizar chunks curtos?**  
Um chunk de 20 caracteres como "Sim." pode ter alta similaridade vetorial, mas não fornece contexto útil para o LLM responder. A penalidade de 15% faz com que ele fique abaixo de chunks mais longos e informativos.

**Exemplo:** Com `minScore = 0.10` e `minLength = 80 chars`:
- Chunk longo com score 0.12 → `adjustedScore = 0.12` (passa)
- Chunk curto com score 0.14 → `adjustedScore = 0.14 * 0.85 = 0.119` (passa, mas com score menor)
- Chunk curto com score 0.11 → `adjustedScore = 0.11 * 0.85 = 0.093` (filtrado — abaixo de 0.10)

### RagConfig — Configuração Dinâmica

```java
public record RagConfig(
        int topK,              // quantos chunks retornar (ex: 3)
        double minScore,       // score mínimo para incluir chunk (ex: 0.10)
        int shortChunkLength,  // comprimento mínimo para não penalizar (ex: 80)
        boolean hybridEnabled, // usar busca híbrida?
        double semanticWeight, // peso da busca vetorial (ex: 0.85 = 85%)
        long monthlyTokenLimit,// limite de tokens por usuário/mês (ex: 100000)
        int maxHistoryMessages // mensagens do histórico a incluir no contexto (ex: 6)
) {}
```

Todos esses valores são ajustáveis pelo admin via interface web, sem reiniciar o servidor.

---

## 10. Pipeline de Ingestão de Documentos

Este é o fluxo completo quando o usuário faz upload de um arquivo.

```
Browser
  │ POST /api/documents (multipart/form-data)
  ▼
DocumentController.upload()
  │
  ▼
IngestDocumentService.ingest()
  │ ├── Verifica se já existe versão anterior (V26 — versionamento)
  │ ├── Supersede a versão anterior
  │ ├── storagePort.store() → grava em disco + registra no banco (status=PROCESSING)
  │ └── asyncWorker.processAsync() → dispara em background
  │
  ▼ HTTP 202 Accepted (resposta imediata)
Browser recebe: { "documentId": "...", "status": "PROCESSING" }

[Em background, AsyncIngestWorker executa:]
  │ storagePort.retrieve() → lê arquivo do disco
  │ parser.extractText()   → extrai texto do PDF/CSV
  │ chunkingStrategy.split() → divide em chunks
  │ embeddingPort.embed()  → converte cada chunk em vetor
  │ vectorStorePort.save() → salva no banco
  └── storagePort.updateProcessingStatus("READY") ou "FAILED"

Browser faz polling: GET /api/documents/{id}/status
```

### ChunkingStrategy — Dividindo o Texto

```java
// application/service/ChunkingStrategy.java
@Component
public class ChunkingStrategy {

    private final int chunkSize;  // padrão: 300 palavras
    private final int overlap;    // padrão: 50 palavras de sobreposição

    public List<String> split(String text) {
        if (text == null || text.isBlank()) return List.of();

        // Normaliza espaços múltiplos e quebras de linha
        String normalized = text.replaceAll("\\s+", " ").trim();
        String[] words = normalized.split(" ");

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            String chunk = String.join(" ", Arrays.copyOfRange(words, start, end));
            if (!chunk.isBlank()) chunks.add(chunk);
            if (end == words.length) break;
            start = end - overlap;  // próximo chunk começa 50 palavras antes do fim
        }

        return chunks;
    }
}
```

**Por que sobreposição (overlap)?**  
Se dividirmos em blocos exatos, informações importantes podem cair no limite entre dois blocos. Com overlap de 50 palavras, o início do próximo chunk repete o final do anterior. Isso garante que nenhuma informação seja perdida.

```
Texto:  [palavra1 ... palavra300 | palavra251 ... palavra550 | palavra501 ...]
                                    ^--- overlap ---^
```

**`Arrays.copyOfRange(words, start, end)`** — cria um subarray de `start` (inclusivo) até `end` (exclusivo). Equivale a `words[start..end-1]`.

**`String.join(" ", ...)`** — junta as palavras com espaço entre elas.

**Referência:** [Java Arrays](https://docs.oracle.com/en/java/docs/api/java.base/java/util/Arrays.html)

### AsyncIngestWorker — Processamento em Background

```java
// application/service/AsyncIngestWorker.java
@Component
public class AsyncIngestWorker {

    @Async("ingestExecutor")   // executa em thread pool separado
    public void processAsync(String documentId, String fileName,
                             String mimeType, String namespaceId, int version) {
        try {
            // 1. Recupera o arquivo do disco
            InputStream stored = storagePort.retrieve(documentId);

            // 2. Seleciona o parser certo para o tipo de arquivo
            DocumentParserPort parser = parsers.stream()
                    .filter(p -> p.supports(mimeType))       // "application/pdf" ou "text/csv"
                    .findFirst()
                    .orElseThrow(() -> new UnsupportedDocumentTypeException(mimeType));

            // 3. Extrai o texto
            String rawText = parser.extractText(stored, fileName);

            // 4. Divide em chunks
            List<String> chunks = chunkingStrategy.split(rawText);

            // 5. Para cada chunk: gera embedding e salva no banco
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                List<Float> embedding = embeddingPort.embed(chunkText);  // chamada à API Google
                vectorStorePort.save(new Chunk(
                        UUID.randomUUID().toString(),
                        documentId,
                        chunkText,
                        embedding,
                        Map.of(
                            "source",      fileName,
                            "namespace",   namespaceId,
                            "version",     String.valueOf(version),
                            "chunkIndex",  String.valueOf(i),
                            "totalChunks", String.valueOf(chunks.size())
                        )
                ));
            }

            // 6. Marca como concluído
            storagePort.updateProcessingStatus(documentId, "READY");

        } catch (Exception e) {
            log.error("[async] Falha: {}", e.getMessage(), e);
            storagePort.updateProcessingStatus(documentId, "FAILED");
        }
    }
}
```

**`@Async("ingestExecutor")`** — executa o método em um thread pool separado. O método retorna imediatamente; o trabalho real acontece em outra thread. `"ingestExecutor"` é o nome do `ThreadPoolTaskExecutor` configurado em `AsyncConfig`.

**Por que um componente separado do `IngestDocumentService`?**  
O Spring implementa `@Async` via proxy — cria um objeto wrapper ao redor do bean. O proxy só funciona em chamadas **de fora** do bean. Se `IngestDocumentService` chamasse um método `@Async` em si mesmo, o proxy não seria ativado e o método rodaria na mesma thread. Separar em `AsyncIngestWorker` garante que o proxy funcione.

**`parsers.stream().filter(p -> p.supports(mimeType)).findFirst()`** — o Spring injeta automaticamente **todos** os beans que implementam `DocumentParserPort` em uma `List<DocumentParserPort>`. Para cada parser, chamamos `supports(mimeType)` e pegamos o primeiro que retornar `true`.

**Referência:** [Spring @Async](https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async)

**Exemplo em outro contexto:** Um sistema de importação de notas fiscais usaria `@Async` para processar os XMLs em background, enquanto o usuário recebe `HTTP 202` imediatamente.

### IngestDocumentService — Versionamento

```java
// application/service/IngestDocumentService.java
@Override
public IngestResult ingest(String fileName, String mimeType, InputStream content,
                           long sizeBytes, String namespaceId) {

    // Verifica se já existe documento com esse nome no namespace
    Optional<Document> existing = storagePort.findActiveByFileName(fileName, namespaceId);
    int newVersion = 1;
    String originalDocumentId = null;

    if (existing.isPresent()) {
        Document prev = existing.get();
        newVersion = prev.version() + 1;         // v1 → v2
        originalDocumentId = prev.rootId();       // mantém referência à versão original

        // Remove chunks da versão anterior (soft delete)
        vectorStorePort.deleteByDocumentId(prev.id());
        // Marca documento anterior como SUPERSEDED
        storagePort.supersede(prev.id());
    }

    // Grava o novo documento (status=PROCESSING)
    String documentId = storagePort.store(
            fileName, mimeType, sizeBytes, content,
            namespaceId, newVersion, originalDocumentId
    );

    // Dispara processamento em background
    asyncWorker.processAsync(documentId, fileName, mimeType, namespaceId, newVersion);

    // Retorna imediatamente com HTTP 202
    return new IngestResult(documentId, newVersion, "PROCESSING");
}
```

**`Optional<Document>`** — o tipo `Optional` do Java 8+ representa "pode ter ou não ter um valor". É mais seguro que retornar `null`, que causaria `NullPointerException` se não for verificado.

**Por que soft delete da versão anterior?**  
O banco mantém o histórico de versões (status `SUPERSEDED`). O usuário pode ver quais documentos foram substituídos e quando. Apenas chunks e documentos `ACTIVE` aparecem nas buscas.

---

## 11. Pipeline de Chat — O Coração do RAG

```
Browser
  │ POST /api/chat { "question": "...", "sessionId": "...", "namespaceId": "..." }
  ▼
ChatController.chat()
  │
  ▼
QueryChatService.queryWithSession()
  │
  ├── 1. ragConfigService.getConfig()           → lê configuração atual (cache 30s)
  ├── 2. tokenUsageService.checkLimit()         → bloqueia se quota esgotada (HTTP 429)
  ├── 3. chatHistoryService.getOrCreateSession() → obtém/cria sessão de conversa
  ├── 4. chatHistoryService.loadRecentMessages() → últimas 6 mensagens do histórico
  ├── 5. documentStoragePort.findByNamespace()  → verifica se há documentos no namespace
  ├── 6. embeddingPort.embed(question)          → converte pergunta em vetor (Google)
  ├── 7. retrieveAndRerank()                    → busca + re-ranking
  │     ├── fetchScored()                       → busca híbrida ou vetorial pura
  │     ├── adjustedScore() para cada chunk     → penaliza chunks curtos
  │     ├── filtra por minScore                 → remove irrelevantes
  │     └── retorna top-K chunks
  ├── 8. piiMaskingService.maskWithStats()      → mascara CPF/email/telefone antes do LLM
  ├── 9. llmPort.complete(systemPrompt+context, history) → chama Groq (LLM)
  ├── 10. chatHistoryService.saveUserMessage()  → persiste no banco
  ├── 11. chatHistoryService.saveAssistantMessage() → persiste no banco
  ├── 12. observabilityService.recordInteraction() → registra para analytics
  └── 13. tokenUsageService.record()            → registra tokens consumidos (fire-and-forget)
```

### QueryChatService — Código Completo Explicado

```java
// application/service/QueryChatService.java

// System prompt: instruções fixas para o LLM sobre como se comportar
private static final String SYSTEM_PROMPT = """
        Você é um especialista operacional sênior...
        1. USE EXCLUSIVAMENTE as informações fornecidas no contexto.
        2. ENTREGUE a informação de forma direta e autoritativa.
        3. NUNCA mencione nomes de arquivos ou metadados.
        5. SE a informação não estiver disponível: "Não tenho essa informação..."
        6. TOKENS como [CPF], [EMAIL] são dados protegidos pela LGPD.
        7. Responda sempre em português do Brasil.
        """;
```

**O que é um system prompt?**  
É a "personalidade" do modelo. As instruções no system prompt têm prioridade sobre as perguntas do usuário. O LLM vai segui-las independentemente do que o usuário perguntar.

**Por que "nunca mencione nomes de arquivos"?**  
Experiência do usuário — o usuário quer a resposta, não saber de qual arquivo veio.

```java
@Override
public ChatResult queryWithSession(String question, String sessionId,
                                   String username, String namespaceId) {
    // Lê configuração do banco (cache de 30s — sem hit toda mensagem)
    RagConfig cfg = ragConfigService.getConfig();

    // Bloqueia antes de qualquer processamento se quota esgotada
    tokenUsageService.checkLimit(username);

    // Obtém ou cria a sessão de conversa
    ChatSession session = chatHistoryService.getOrCreateSession(sessionId, username);
    // Carrega as últimas N mensagens (N = cfg.maxHistoryMessages())
    List<ChatMessage> dbHistory = chatHistoryService.loadRecentMessages(session.id());

    // Se não há documentos no namespace, responde com mensagem padrão
    if (documentStoragePort.findByNamespace(namespaceId).isEmpty()) {
        String noDocsAnswer = "Nenhum documento foi encontrado neste namespace...";
        // ... salva e retorna
    }

    // Gera o embedding da pergunta (vetor numérico)
    List<Float> queryEmbedding = embeddingPort.embed(question);

    // Busca e re-ranking dos chunks mais relevantes
    List<Chunk> relevantChunks = retrieveAndRerank(queryEmbedding, question, namespaceId, cfg);
```

```java
    // Monta o contexto: texto dos chunks com PII mascarada
    String context = relevantChunks.stream()
            .map(c -> {
                PiiMaskingService.MaskResult masked = piiMaskingService.maskWithStats(c.content());
                // "Fonte: manual.pdf" - o LLM vê mas o system prompt impede de citar
                return String.format("[Fonte: %s]\n%s",
                        c.metadata().getOrDefault("source", "interno"),
                        masked.maskedText());
            })
            .collect(Collectors.joining("\n\n---\n\n"));  // separa chunks com ---
```

**`Collectors.joining("\n\n---\n\n")`** — une todos os chunks em uma string, separados por uma linha com `---`. O LLM recebe um contexto legível:

```
[Fonte: manual.pdf]
O procedimento de segurança exige...

---

[Fonte: politicas.csv]
Em caso de emergência, contatar [TELEFONE]...
```

```java
    // Extrai nomes únicos dos arquivos-fonte (para exibir no frontend como "chips")
    List<String> sources = relevantChunks.stream()
            .map(c -> c.metadata().getOrDefault("source", ""))
            .filter(s -> !s.isBlank())
            .distinct()    // remove duplicatas
            .collect(Collectors.toList());

    // Monta a conversa: histórico + pergunta atual
    List<ChatMessage> conversation = buildConversation(dbHistory, question);

    // Chama o LLM (Groq) com system prompt + contexto + conversa
    String answer = llmPort.complete(SYSTEM_PROMPT + "\n\nCONTEXTO:\n" + context, conversation);
```

```java
    // Re-ranking — filtra e ordena os chunks
    private List<Chunk> retrieveAndRerank(List<Float> queryEmbedding, String question,
                                           String namespaceId, RagConfig cfg) {
        int fetchK = cfg.topK() * 3;   // busca 3x mais para re-ranking (ex: topK=3 → busca 9)
        List<ScoredChunk> scored = fetchScored(queryEmbedding, question, fetchK, namespaceId, cfg);

        return scored.stream()
                .filter(sc -> sc.adjustedScore(cfg.shortChunkLength()) >= cfg.minScore())
                .sorted(Comparator.comparingDouble(
                        (ScoredChunk sc) -> sc.adjustedScore(cfg.shortChunkLength())).reversed())
                .limit(cfg.topK())     // retorna apenas os top-K
                .map(ScoredChunk::chunk)
                .collect(Collectors.toList());
    }
```

**Por que `topK * 3` no fetch?**  
Após o re-ranking (penalidade de chunks curtos + filtro de minScore), alguns chunks são descartados. Buscar 3x mais garante que os `topK` melhores sempre sejam encontrados.

**`Comparator.comparingDouble(...).reversed()`** — ordena do maior score para o menor (decrescente).

**`ScoredChunk::chunk`** — method reference. Equivale a `sc -> sc.chunk()`.

```java
    private List<ScoredChunk> fetchScored(List<Float> queryEmbedding, String question,
                                           int fetchK, String namespaceId, RagConfig cfg) {
        if (cfg.hybridEnabled()) {
            try {
                return vectorStorePort.findSimilarHybrid(
                        queryEmbedding, question, fetchK, cfg.semanticWeight(), namespaceId);
            } catch (Exception e) {
                // Se a busca híbrida falhar, cai na busca vetorial pura
                log.warn("Busca híbrida falhou, usando vetorial pura: {}", e.getMessage());
            }
        }
        return vectorStorePort.findSimilar(queryEmbedding, fetchK, namespaceId);
    }
```

**Por que o `try-catch` com fallback?**  
A busca híbrida usa funções SQL específicas do PostgreSQL (`to_tsvector`). Em alguns ambientes (Supabase, PostgreSQL mínimo), essas funções podem não estar disponíveis. O fallback garante que o sistema continua funcionando mesmo se a busca híbrida falhar.

**Padrão de design:** Circuit Breaker / Fallback. Quando a operação principal falha, usa uma alternativa degradada mas funcional.

---

## 12. Adaptadores de IA — Groq e Google

### GroqLlmAdapter — Chamando o LLM com Resiliência

```java
// adapter/out/ai/groq/GroqLlmAdapter.java
@Component
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "groq")
public class GroqLlmAdapter implements LlmPort {
```

**`@ConditionalOnProperty`** — este bean só é criado se `ragbot.ai.provider: groq` no `application.yml`. Para trocar de provedor, basta mudar a configuração — o Spring automaticamente usa outro adapter.

**Referência:** [Spring Boot Conditional Annotations](https://docs.spring.io/spring-boot/reference/features/developing-auto-configuration.html#features.developing-auto-configuration.condition-annotations)

```java
    public GroqLlmAdapter(...) {
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY nao configurada.");
        }

        // HttpClient com timeout de conexão e resposta
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(timeoutSeconds))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeoutSeconds * 1000);

        // WebClient: cliente HTTP reativo com configurações globais
        this.webClient = WebClient.builder()
                .baseUrl(endpoint)                              // "https://api.groq.com/openai/v1"
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader("Authorization", "Bearer " + token) // autenticação padrão
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
```

**Por que `throw new IllegalStateException` no construtor?**  
Fail-fast: melhor detectar o problema na inicialização do que em runtime, quando um usuário está usando o sistema. O Spring Boot não inicia se lançar exceção no construtor do bean.

**`Duration.ofSeconds(30)`** — `Duration` é a forma moderna do Java de representar durações. Mais legível que `30 * 1000L`.

```java
    @Override
    public String complete(String systemPrompt, List<ChatMessage> history) {
        List<MessageDto> messages = new ArrayList<>();
        messages.add(new MessageDto("system", systemPrompt));  // primeiro vem o system
        history.forEach(m -> messages.add(
                new MessageDto(m.role(), m.content())));       // depois o histórico

        ChatResponseDto response = webClient.post()
                .uri("/chat/completions")               // endpoint da API Groq (compatível OpenAI)
                .bodyValue(new ChatRequestDto(model, messages, 1024, 0.3))
                .retrieve()

                // 429 Rate Limit: falha imediatamente com mensagem clara
                .onStatus(s -> s.value() == 429,
                    r -> r.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new RateLimitException("Groq rate limit atingido..."))))

                // 5xx Erros do servidor: retenta com backoff exponencial
                .onStatus(s -> RETRYABLE.contains(s.value()),
                    r -> r.bodyToMono(String.class).flatMap(body ->
                        Mono.error(new RetryableException(r.statusCode().value() + ": " + body))))

                .bodyToMono(ChatResponseDto.class)
                .timeout(timeout)

                // Retry: até 3 tentativas, apenas para RetryableException
                .retryWhen(Retry.backoff(maxRetries, Duration.ofMillis(initialBackoffMs))
                        .filter(t -> t instanceof RetryableException)
                        .doBeforeRetry(s -> log.warn("Groq retry {}/{}", s.totalRetries() + 1, maxRetries)))

                // Unwrap: expõe a causa original em vez de RetryExhaustedException
                .onErrorMap(Exceptions::isRetryExhausted, t -> {
                    Throwable cause = t.getCause();
                    return cause instanceof RuntimeException re ? re
                            : new RuntimeException("Groq retries esgotados", cause);
                })
                .block();   // bloqueia até ter a resposta (converte reativo → síncrono)
```

**`Retry.backoff(3, Duration.ofMillis(500))`** — retry com backoff exponencial:
- 1ª tentativa falha → aguarda 500ms → 2ª tentativa
- 2ª tentativa falha → aguarda 1000ms → 3ª tentativa
- 3ª tentativa falha → aguarda 2000ms → 4ª tentativa
- 4ª falha → lança exceção

**Por que 429 NÃO é retentado?**  
Comentário no código: "retentar 429 multiplica o consumo e piora o bloqueio (efeito cascata)". O rate limit da Groq é por minuto — aguardar 500ms e tentar de novo ainda está dentro do mesmo minuto, e vai continuar falhando.

**`Mono.error(new ...)`** — em programação reativa, em vez de lançar exceção diretamente, você retorna um `Mono` que representa um erro. O WebClient vai propagar esse erro pela cadeia reativa.

**`.block()`** — converte o resultado reativo (`Mono<T>`) em síncrono. O código do serviço é síncrono, então precisamos esperar a resposta.

**Referência:** [Spring WebFlux Retry](https://projectreactor.io/docs/core/release/reference/#_retrying)

**Exemplo em outro contexto:** Uma integração com a API dos Correios usaria o mesmo padrão — timeout de 10s, retry em 503 (serviço indisponível), sem retry em 400 (CEP inválido).

### GoogleEmbeddingAdapter — Convertendo Texto em Vetor

```java
// adapter/out/ai/google/GoogleEmbeddingAdapter.java
@Override
public List<Float> embed(String text) {
    EmbedResponseDto response = webClient.post()
            .uri(uriBuilder -> uriBuilder
                    .path("/models/{model}:embedContent")
                    .queryParam("key", token)               // API key na URL
                    .build(normalizeModel(model)))           // "gemini-embedding-001"
            .bodyValue(new EmbedRequestDto(
                    "RETRIEVAL_QUERY",                      // tipo de tarefa
                    new ContentDto(List.of(new PartDto(text)))
            ))
            .retrieve()
            .onStatus(HttpStatusCode::isError, clientResponse ->
                    clientResponse.bodyToMono(String.class).flatMap(body ->
                            Mono.error(new RuntimeException(
                                    "Google Embedding API error: " + body))))
            .bodyToMono(EmbedResponseDto.class)
            .block();

    return response.embedding().values();   // lista de 3072 floats
}

// DTOs internos — usados apenas para serialização/deserialização JSON
record EmbedRequestDto(String taskType, ContentDto content) {}
record ContentDto(List<PartDto> parts) {}
record PartDto(String text) {}
record EmbedResponseDto(EmbeddingDto embedding) {}
record EmbeddingDto(List<Float> values) {}
```

**`record` como DTO interno:** Os records `EmbedRequestDto` etc. são classes internas privadas — usados apenas para serializar o JSON para a API do Google e deserializar a resposta. Jackson (biblioteca de JSON) sabe serializar/deserializar records automaticamente.

**`"RETRIEVAL_QUERY"`** — o Google Gemini tem diferentes modos de embedding:
- `RETRIEVAL_QUERY`: otimizado para perguntas que serão comparadas com documentos
- `RETRIEVAL_DOCUMENT`: otimizado para documentos que serão indexados

Usar o tipo correto melhora a qualidade da busca.

**Referência:** [Google Embedding API](https://ai.google.dev/api/embeddings)

---

## 13. Serviços de Suporte

### PiiMaskingService — Protegendo Dados Pessoais (LGPD)

```java
// application/service/PiiMaskingService.java

// Expressões regulares compiladas uma única vez (eficiente)
private static final Pattern CPF = Pattern.compile(
        "\\b(\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}|\\d{11})\\b");
        // \\b = word boundary (limite de palavra)
        // \\d{3} = exatamente 3 dígitos
        // | = ou
        // Aceita: 123.456.789-01 ou 12345678901

private static final Pattern EMAIL = Pattern.compile(
        "\\b[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}\\b");

public MaskResult maskWithStats(String text) {
    AtomicInteger count = new AtomicInteger(0);

    String result = text;
    result = replaceAndCount(CNPJ,  result, "[CNPJ]",    count);  // CNPJ antes de CPF!
    result = replaceAndCount(CPF,   result, "[CPF]",     count);
    result = replaceAndCount(EMAIL, result, "[EMAIL]",   count);
    result = replaceAndCount(PHONE, result, "[TELEFONE]",count);
    result = replaceAndCount(MONEY, result, "[VALOR]",   count);

    return new MaskResult(result, count.get());
}

private String replaceAndCount(Pattern pattern, String text, String replacement, AtomicInteger counter) {
    return pattern.matcher(text).replaceAll(match -> {
        counter.incrementAndGet();   // conta cada substituição
        return replacement;
    });
}
```

**Por que CNPJ antes de CPF?**  
CNPJ contém dígitos que poderiam fazer match parcial com o padrão CPF. Processando CNPJ primeiro, evitamos que parte do CNPJ seja substituída como CPF.

**`Pattern.compile(...)`** — compilar regex é caro. Compilar uma vez como `static final` e reutilizar é muito mais eficiente do que compilar a cada chamada.

**`\\b`** — "word boundary". Garante que `12345678901` não faça match dentro de `1234567890123456` (número de cartão de crédito). Sem `\\b`, `CPF.matcher("1234567890123").find()` daria `true` (errado).

**Por que mascarar antes de enviar para o LLM?**  
A LGPD (Lei Geral de Proteção de Dados) proíbe compartilhar dados pessoais com terceiros sem consentimento. O Groq (empresa americana) é um terceiro. Mascarar garante que CPFs, emails e telefones não saiam do Brasil.

**Referência:** [Java Regex Pattern](https://docs.oracle.com/en/java/docs/api/java.base/java/util/regex/Pattern.html)

### TokenUsageService — Controle de Quota

```java
// application/service/TokenUsageService.java

public void checkLimit(String username) {
    long limit = ragConfigService.getConfig().monthlyTokenLimit();  // ex: 100.000
    long used  = port.getMonthlyUsage(username, currentYearMonth()); // ex: 95.230
    if (used >= limit) {
        throw new TokenLimitExceededException(username, used, limit); // → HTTP 429
    }
}

public void record(String username, String question, String answer) {
    try {
        long tokens = estimate(question) + estimate(answer);
        port.incrementUsage(username, currentYearMonth(), tokens);
    } catch (Exception e) {
        // Fire-and-forget: falha silenciosa
        log.error("Falha ao registrar tokens: {}", e.getMessage());
    }
}

private long estimate(String text) {
    // Heurística: palavras × 1.3 (aproximação de tokens)
    return (long) (text.split("\\s+").length * 1.3);
}

private int currentYearMonth() {
    LocalDate now = LocalDate.now();
    return now.getYear() * 100 + now.getMonthValue();
    // Ex: maio/2026 → 202605
}
```

**"Fire-and-forget"** — chamar e esquecer. O registro de tokens não deve falhar o chat do usuário. Se o banco estiver lento, a resposta já foi enviada. Por isso, erros aqui são apenas logados.

**`LocalDate.now().getYear() * 100 + now.getMonthValue()`** — gera um inteiro `YYYYMM` para identificar o mês. Maio/2026 → `202605`. Simples e eficiente para agrupar uso por mês.

**Estimativa de tokens:** A proporção real palavra/token varia por idioma e modelo. Para português, 1 token ≈ 0.7-1.0 palavra. Multiplicar por 1.3 dá uma estimativa conservadora (levemente superestimada — melhor que subestimar).

**Referência:** [OpenAI Tokenization](https://platform.openai.com/tokenizer)

---

## 14. Controllers REST

### ChatController — Endpoint de Chat

```java
// adapter/in/rest/ChatController.java
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @PostMapping
    public ResponseEntity<RestDtos.ChatResponse> chat(
            @RequestBody RestDtos.ChatRequest request,   // body JSON
            Authentication auth) {                        // usuário autenticado

        String username = auth != null ? auth.getName() : "anônimo";
        String namespaceId = request.namespaceId() != null
                ? request.namespaceId()
                : Namespace.DEFAULT_ID;

        QueryChatUseCase.ChatResult result = queryChatUseCase.queryWithSession(
                request.question(), request.sessionId(), username, namespaceId);

        return ResponseEntity.ok(new RestDtos.ChatResponse(
                result.answer(), result.interactionId(), result.sessionId(), result.sources()));
    }
```

**`@RestController`** — combina `@Controller` + `@ResponseBody`. Todo método retorna JSON automaticamente.

**`@RequestMapping("/api/chat")`** — prefixo da URL para todos os métodos da classe.

**`@PostMapping`** — método `POST` no path da classe (`/api/chat`).

**`@RequestBody`** — deserializa o JSON do body da requisição para o objeto Java.

**`Authentication auth`** — o Spring injeta o objeto de autenticação do usuário logado. O filtro JWT populou isso antes do controller ser chamado.

**`auth.getName()`** — retorna o username do JWT (o campo `subject` do token).

**`ResponseEntity.ok(...)`** — retorna HTTP 200 com o objeto JSON.

**Referência:** [Spring MVC @RestController](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)

### DocumentController — Upload e Versionamento

```java
// adapter/in/rest/DocumentController.java

@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public ResponseEntity<RestDtos.IngestResponse> upload(
        @RequestParam("file") MultipartFile file,        // arquivo enviado
        @RequestParam(value = "namespaceId",
                      defaultValue = Namespace.DEFAULT_ID) String namespaceId,
        Authentication auth,
        HttpServletRequest request) throws IOException {

    IngestDocumentUseCase.IngestResult result = ingestUseCase.ingest(
            file.getOriginalFilename(),
            file.getContentType(),
            file.getInputStream(),
            file.getSize(),
            namespaceId
    );

    // Registra no audit log (quem fez upload de qual arquivo)
    auditLogService.record(actor, AuditAction.DOCUMENT_UPLOAD, result.documentId(), "DOCUMENT",
            "arquivo=" + file.getOriginalFilename() + " v" + result.version(),
            request.getRemoteAddr());

    // HTTP 202 Accepted — processamento acontece em background
    return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(new RestDtos.IngestResponse(...));
}
```

**`consumes = MediaType.MULTIPART_FORM_DATA_VALUE`** — aceita apenas requisições `multipart/form-data` (padrão para upload de arquivos).

**`@RequestParam("file") MultipartFile file`** — o Spring extrai o arquivo do form-data. `MultipartFile` dá acesso ao nome, tipo, tamanho e conteúdo do arquivo.

**`file.getInputStream()`** — retorna o conteúdo do arquivo como stream. Não carrega tudo na memória — processa aos poucos.

**`HttpStatus.ACCEPTED` (202)** — significa "recebi sua requisição, estou processando". Diferente de `200 OK` que significa "já terminei". O 202 informa ao cliente que ele deve verificar o status depois.

**Polling de status:**
```java
@GetMapping("/{id}/status")
public ResponseEntity<RestDtos.DocumentStatusResponse> status(@PathVariable String id) {
    return listUseCase.findById(id)
            .map(doc -> ResponseEntity.ok(RestDtos.DocumentStatusResponse.from(doc)))
            .orElse(ResponseEntity.notFound().build());
}
```

**`@PathVariable String id`** — extrai o valor de `{id}` da URL. Ex: `GET /api/documents/abc-123/status` → `id = "abc-123"`.

**`.orElse(ResponseEntity.notFound().build())`** — se o documento não existir, retorna `404 Not Found`.

**Referência:** [HTTP 202 Accepted](https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/202)

---

## 15. Como Converter Este Arquivo em PDF

### Opção 1 — VS Code (mais simples)

1. Instale o VS Code: [code.visualstudio.com](https://code.visualstudio.com)
2. Instale a extensão **"Markdown PDF"** (autor: yzane)
3. Abra este arquivo `DOCUMENTACAO_ESTUDO.md`
4. Pressione `Ctrl+Shift+P` → busque "Markdown PDF: Export (PDF)"
5. O PDF é gerado na mesma pasta do arquivo

### Opção 2 — Pandoc (mais profissional)

```bash
# Instala o Pandoc: https://pandoc.org/installing.html
# Depois:
pandoc DOCUMENTACAO_ESTUDO.md -o documentacao.pdf

# Com formatação melhor (requer LaTeX instalado):
pandoc DOCUMENTACAO_ESTUDO.md -o documentacao.pdf --pdf-engine=xelatex
```

### Opção 3 — Node.js (sem instalação extra)

```bash
# Se tiver Node.js instalado:
npx md-to-pdf DOCUMENTACAO_ESTUDO.md
```

### Opção 4 — GitHub

1. Faça commit deste arquivo no repositório
2. Acesse a URL do arquivo no GitHub
3. Use a extensão do Chrome **"GitHub Markdown Printer"**
4. Ctrl+P → Salvar como PDF

---

## Glossário Rápido

| Termo | Significado |
|-------|-------------|
| **RAG** | Retrieval-Augmented Generation — busca documentos e usa a IA para responder |
| **Embedding** | Vetor numérico que representa o significado de um texto |
| **Chunk** | Pedaço de texto (ex: 300 palavras) extraído de um documento |
| **LLM** | Large Language Model — o modelo de IA (ex: Groq, GPT-4) |
| **pgvector** | Extensão do PostgreSQL para busca vetorial |
| **JWT** | JSON Web Token — token de autenticação stateless |
| **BCrypt** | Algoritmo de hash de senhas (irreversível) |
| **Flyway** | Gerenciador de migrações de banco de dados |
| **Hexagonal** | Arquitetura onde o domínio não conhece tecnologias externas |
| **Port** | Interface Java que define um contrato entre domínio e adaptadores |
| **Adapter** | Implementação concreta de uma Port (ex: `GroqLlmAdapter` implementa `LlmPort`) |
| **Soft Delete** | Marcar registro como deletado em vez de apagar fisicamente |
| **Namespace** | Agrupamento de documentos (ex: por departamento) |
| **PII** | Personally Identifiable Information — dados pessoais (CPF, email...) |
| **LGPD** | Lei Geral de Proteção de Dados — lei brasileira equivalente ao GDPR europeu |
| **BM25** | Algoritmo clássico de busca por palavras-chave |
| **Rate Limit** | Limite de requisições por tempo (ex: 20/minuto por usuário) |
| **MDC** | Mapped Diagnostic Context — contexto de logging por thread |
| **Fire-and-forget** | Executa e não espera o resultado (tolerante a falhas) |
| **Fallback** | Alternativa usada quando a operação principal falha |
| **WebClient** | Cliente HTTP reativo do Spring (substitui RestTemplate) |
| **Mono** | Tipo reativo que representa 0 ou 1 valor assíncrono |
| **IVFFLAT** | Índice aproximado para busca vetorial eficiente |
| **Backoff exponencial** | Estratégia de retry que dobra o tempo de espera a cada tentativa |
| **@Async** | Executa método em thread pool separado (não bloqueia) |
| **@Scheduled** | Executa método periodicamente (cron jobs) |
| **@ConditionalOnProperty** | Bean só criado se uma propriedade tiver determinado valor |
| **record** | Classe Java 16+ imutável com campos, getters e equals automáticos |
| **Optional** | Container que pode conter ou não um valor (evita NullPointerException) |

---

*Documentação gerada em 2026-05-12. Projeto: Crivo AI (RAGBot) — Fase 12.*  
*Stack: Java 21 · Spring Boot 3.2.5 · PostgreSQL 16 + pgvector · Groq · Google Gemini*
