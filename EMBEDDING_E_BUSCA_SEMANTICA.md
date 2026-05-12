# Embedding, Busca Semântica e Como a IA Entende os Seus Documentos

> **Para quem é este documento?**  
> Gestores e desenvolvedores iniciantes que precisam entender o que acontece "por baixo" quando
> o chatbot responde uma pergunta. Sem jargão desnecessário — cada conceito novo é explicado
> com analogias antes de mostrar o código.

---

## Sumário

1. [O Problema que Motivou Tudo Isso](#1-o-problema-que-motivou-tudo-isso)
2. [O que é Embedding — A Ideia Central](#2-o-que-é-embedding--a-ideia-central)
3. [Como o Embedding é Gerado — GoogleEmbeddingAdapter](#3-como-o-embedding-é-gerado--googleembeddingadapter)
4. [Como os Vetores são Armazenados — pgvector](#4-como-os-vetores-são-armazenados--pgvector)
5. [Busca Semântica — Encontrando o que o Usuário Quer Dizer](#5-busca-semântica--encontrando-o-que-o-usuário-quer-dizer)
6. [Busca por Palavras-chave (BM25) — O Complemento da Semântica](#6-busca-por-palavras-chave-bm25--o-complemento-da-semântica)
7. [Busca Híbrida — O Melhor dos Dois Mundos](#7-busca-híbrida--o-melhor-dos-dois-mundos)
8. [Re-ranking — Filtrando o Que Não Serve](#8-re-ranking--filtrando-o-que-não-serve)
9. [Como a LLM Recebe e Usa o Contexto](#9-como-a-llm-recebe-e-usa-o-contexto)
10. [O Pipeline Completo — Do Upload à Resposta](#10-o-pipeline-completo--do-upload-à-resposta)
11. [Troca de Provedores — LLM e Embedding da Empresa](#11-troca-de-provedores--llm-e-embedding-da-empresa)
12. [Deploy Local com Banco de Dados Próprio](#12-deploy-local-com-banco-de-dados-próprio)
13. [Perguntas Frequentes — Railway, Deploy e Supabase](#13-perguntas-frequentes--railway-deploy-e-supabase)

---

## 1. O Problema que Motivou Tudo Isso

### Por que não simplesmente "enviar o documento para a IA"?

Imagine que a empresa tem um manual de 300 páginas. Você quer perguntar: *"Qual é o procedimento
em caso de acidente de trabalho?"*

A solução ingênua seria enviar o manual inteiro para a IA e pedir que ela respondesse.
O problema é que modelos de linguagem têm um limite de quanto texto conseguem processar de
uma vez — chamado de **context window** (janela de contexto).

| Modelo | Context window (aprox.) | Equivale a |
|--------|------------------------|------------|
| GPT-3.5 | 16.000 tokens | ~12.000 palavras |
| GPT-4o | 128.000 tokens | ~96.000 palavras |
| llama-3.3-70b (Groq) | 128.000 tokens | ~96.000 palavras |

Um manual de 300 páginas tem ~150.000 palavras — **não cabe**.

Mesmo que coubesse, enviar 150.000 palavras a cada pergunta seria:
- **Caro:** APIs de IA cobram por token enviado
- **Lento:** mais texto = mais tempo para responder
- **Impreciso:** modelos perdem foco em textos muito longos

### A solução: RAG

Em vez de enviar tudo, o sistema:
1. Divide o manual em pedaços pequenos (chunks)
2. Converte cada pedaço em um vetor numérico (embedding)
3. Quando o usuário pergunta, busca apenas os pedaços relevantes
4. Envia somente esses pedaços para a IA responder

É como ter um bibliotecário que, antes de chamar o especialista, já separou as 3 páginas
mais relevantes do manual.

---

## 2. O que é Embedding — A Ideia Central

### A Metáfora do Mapa

Imagine um mapa onde textos com significados parecidos ficam próximos e textos com
significados diferentes ficam distantes. O **embedding** é uma função que posiciona
qualquer texto nesse mapa.

```
           "prazo máximo"
                 ●
                   "tempo limite de entrega"
                         ●

"procedimento de segurança"
         ●
           "norma de proteção"
                 ●


                                    "receita de lasanha"
                                              ●
                                    "como fazer macarrão"
                                              ●
```

Quando o usuário pergunta *"qual é o prazo?"*, o sistema:
1. Posiciona essa pergunta no mapa (gera o embedding)
2. Encontra os pedaços de texto mais próximos no mapa
3. Retorna esses pedaços como contexto para a IA

### O que é um Vetor de 3072 Dimensões?

Um vetor é simplesmente uma lista de números:

```
"procedimento de segurança" →
[0.1234, -0.5678, 0.9012, 0.3456, -0.7890, 0.2345, ..., 0.6789]
  dim1     dim2    dim3    dim4     dim5    dim6          dim3072
```

Cada número representa alguma característica semântica do texto (urgência, área de negócio,
tom formal/informal, etc.). O modelo de embedding aprendeu essas 3072 dimensões treinando
em bilhões de textos — nenhuma delas tem um significado humano direto.

**Por que 3072?** O modelo Gemini `gemini-embedding-001` do Google gera vetores de 3072
dimensões. Mais dimensões = mais capacidade de capturar nuances semânticas, mas mais espaço
em disco e mais processamento.

### Distância entre Vetores = Diferença de Significado

Para medir o quão parecidos são dois textos, o sistema calcula a **distância de cosseno**
entre os vetores:

```
Distância = 0.0   → textos idênticos (mesmo vetor)
Distância = 0.5   → textos relacionados
Distância = 1.0   → textos completamente diferentes

Score = 1.0 - Distância
Score = 1.0   → idênticos
Score = 0.5   → relacionados
Score = 0.0   → sem relação
```

```
Pergunta: "qual o prazo máximo?"
   vs
Chunk 1: "o tempo limite de entrega é 30 dias"  → score = 0.87  ← muito relevante
Chunk 2: "a documentação deve ser assinada..."  → score = 0.31  ← pouco relevante
Chunk 3: "receita de bolo de chocolate"         → score = 0.04  ← irrelevante
```

A busca retorna apenas os chunks com score acima de 0.10 (configurável pelo admin).

---

## 3. Como o Embedding é Gerado — GoogleEmbeddingAdapter

Quando o sistema precisa converter texto em vetor, ele chama a API do Google:

```java
// adapter/out/ai/google/GoogleEmbeddingAdapter.java
@Override
public List<Float> embed(String text) {
    EmbedResponseDto response = webClient.post()
            .uri("/models/gemini-embedding-001:embedContent")
            .bodyValue(new EmbedRequestDto(
                    "RETRIEVAL_QUERY",   // tipo: pergunta sendo comparada com documentos
                    new ContentDto(List.of(new PartDto(text)))
            ))
            .retrieve()
            .bodyToMono(EmbedResponseDto.class)
            .block();

    return response.embedding().values();  // lista de 3072 floats
}
```

### O que acontece na prática

**Para indexar um chunk de documento:**
```
Texto: "O prazo máximo para entrega de documentação é de 30 dias corridos."
  ↓ (chamada à API do Google — ~200ms)
Vetor: [0.1234, -0.5678, 0.9012, ..., 0.6789]  (3072 números)
  ↓
Salvo no PostgreSQL na coluna embedding
```

**Para uma pergunta do usuário:**
```
Pergunta: "qual é o prazo para entrega?"
  ↓ (chamada à API do Google — ~200ms)
Vetor: [0.1198, -0.5643, 0.8987, ..., 0.6812]  (3072 números — muito próximo do anterior)
  ↓
Comparado com todos os vetores no banco
```

### Dois tipos de tarefa

O Google Gemini tem dois modos para embedding:

| Tipo | Quando usar | Por quê |
|------|-------------|---------|
| `RETRIEVAL_DOCUMENT` | Na ingestão (indexando chunks) | Otimizado para representar conteúdo a ser recuperado |
| `RETRIEVAL_QUERY` | Na busca (embedding da pergunta) | Otimizado para comparar com documentos |

Usar o tipo correto melhora a precisão da busca. É como padronizar a unidade de medida —
se você mede documentos em metros e perguntas em centímetros, a comparação fica errada.

### O contrato (interface) que permite trocar de provedor

```java
// application/port/out/EmbeddingPort.java
public interface EmbeddingPort {
    List<Float> embed(String text);
}
```

Essa interface de **uma linha** é o que separa o negócio da tecnologia. O `QueryChatService`
e o `AsyncIngestWorker` chamam apenas `embeddingPort.embed(text)` — sem saber se é Google,
OpenAI ou a LLM da empresa.

---

## 4. Como os Vetores são Armazenados — pgvector

### O que é pgvector?

pgvector é uma extensão do PostgreSQL que adiciona:
1. Um tipo de dado `vector(n)` — coluna que armazena listas de números
2. Operadores de distância (`<=>`, `<->`, `<#>`)
3. Índices especializados para busca rápida em vetores

```sql
-- Habilitar a extensão (feito uma vez na V1__create_tables.sql)
CREATE EXTENSION IF NOT EXISTS vector;

-- Coluna de embedding na tabela chunks
CREATE TABLE chunks (
    id          VARCHAR(36) PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(3072),        -- ← tipo do pgvector, 3072 dimensões
    metadata    JSONB NOT NULL DEFAULT '{}'
);
```

### O Índice IVFFLAT — Busca Rápida Aproximada

```sql
CREATE INDEX idx_chunks_embedding ON chunks
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
```

Sem índice: para cada busca, o PostgreSQL compara o vetor da pergunta com **todos**
os vetores da tabela. Com 100.000 chunks, seriam 100.000 comparações por pergunta.

Com IVFFLAT: o índice divide os vetores em 100 grupos (listas). A busca só compara
com o grupo mais próximo — cerca de 1000 comparações. **100x mais rápido.**

O trade-off: é uma busca **aproximada** (pode perder 1-5% dos resultados mais relevantes).
Para um chatbot, isso é completamente aceitável.

**GIN vs IVFFLAT:**

| Índice | Usado para | Tipo de busca |
|--------|-----------|---------------|
| IVFFLAT | `embedding vector(3072)` | Similaridade vetorial (semântica) |
| GIN | `content_tsvector` | Busca por palavras-chave (BM25) |

### Como o Java Converte Lista para SQL

O PostgreSQL aceita vetores no formato string `[n1,n2,n3,...]`:

```java
// PgVectorStoreAdapter.java
private String toVectorString(List<Float> embedding) {
    return "[" + embedding.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(",")) + "]";
}
// List<Float>[0.1234f, -0.5678f, ...] → "[0.1234,-0.5678,...]"
```

No SQL:
```sql
-- ?::vector faz o cast da string para o tipo vector do pgvector
WHERE embedding <=> ?::vector < 0.5
-- ou
ORDER BY embedding <=> ?::vector   -- ordena por distância (menor = mais próximo)
```

---

## 5. Busca Semântica — Encontrando o que o Usuário Quer Dizer

### Por que busca semântica é diferente de busca por palavra-chave

| Pergunta do usuário | Texto no documento | Busca por palavra | Busca semântica |
|--------------------|--------------------|-------------------|-----------------|
| "qual o prazo?" | "tempo máximo é 30 dias" | ❌ não encontra | ✅ encontra |
| "como me demitem?" | "processo de desligamento" | ❌ não encontra | ✅ encontra |
| "quem é responsável?" | "o gestor direto é o encarregado" | ❌ não encontra | ✅ encontra |
| "CPF do João" | "CPF do João" | ✅ encontra | ✅ encontra |

### O SQL da Busca Vetorial Pura

```java
// PgVectorStoreAdapter.java — findSimilar()
public List<ScoredChunk> findSimilar(List<Float> queryEmbedding, int topK, String namespaceId) {
    return jdbc.query(
            """
            SELECT
                c.id,
                c.document_id,
                c.content,
                c.embedding::text,
                c.metadata::text,
                1.0 - (c.embedding <=> ?::vector) AS score   -- score = 1 - distância
            FROM chunks c
            JOIN documents d ON c.document_id = d.id
            WHERE c.status = 'ACTIVE'
              AND d.status = 'ACTIVE'
              AND d.namespace_id = ?             -- isola por departamento/namespace
            ORDER BY c.embedding <=> ?::vector   -- ordena pelo mais próximo (menor distância)
            LIMIT ?                              -- retorna só os topK melhores
            """,
            this::mapScoredRow,
            vectorStr, namespaceId, vectorStr, topK
    );
}
```

**Operador `<=>`** — distância de cosseno. É o coração de tudo. O pgvector registra
este operador especial que o PostgreSQL padrão não tem.

**`1.0 - (c.embedding <=> ?::vector)`** — transforma distância em score:
- Distância 0.0 (idêntico) → score 1.0
- Distância 0.5 → score 0.5
- Distância 1.0 (oposto) → score 0.0

**`JOIN documents d ON c.document_id = d.id`** — garante que chunks de documentos
deletados ou de outros namespaces nunca apareçam nos resultados.

---

## 6. Busca por Palavras-chave (BM25) — O Complemento da Semântica

### O que é BM25?

BM25 (Best Match 25) é o algoritmo de busca por palavras-chave mais usado no mundo —
é o que o Google usava internamente antes das redes neurais, e que o Elasticsearch
ainda usa como base.

**Diferença de uma busca LIKE simples:**

| Busca | `WHERE content LIKE '%segurança%'` | BM25 |
|-------|------------------------------------|------|
| O que faz | Texto contém a string exata | Pontua relevância por frequência e raridade da palavra |
| "segurança" em 1 de 100 docs | Retorna todos que contêm | Pontua alto (palavra rara = mais específica) |
| "o" em 100 de 100 docs | Retorna todos que contêm | Pontua baixo (palavra comum = pouco informativa) |
| Typos e variações | ❌ | ❌ (BM25 também não, diferente de fuzzy search) |

### Como o PostgreSQL Implementa BM25

```sql
-- V8__hybrid_search.sql
-- Coluna computada automaticamente — PostgreSQL mantém atualizada a cada INSERT/UPDATE
ALTER TABLE chunks
    ADD COLUMN content_tsvector tsvector
    GENERATED ALWAYS AS (to_tsvector('simple', content)) STORED;

-- Índice GIN para busca eficiente
CREATE INDEX idx_chunks_tsvector ON chunks USING GIN (content_tsvector);
```

**`to_tsvector('simple', content)`** — converte o texto em um vetor de palavras:
```
"O prazo máximo para entrega é 30 dias"
  ↓ to_tsvector('simple', ...)
'30':7 'dias':8 'entrega':6 'e':5 'maximo':3 'o':1 'para':4 'prazo':2
```

Remove stop words, normaliza, e anota a posição de cada palavra.

**`plainto_tsquery('simple', 'prazo entrega')`** — converte a pergunta em query de busca:
```
"prazo entrega" → 'prazo' & 'entrega'  (documentos que contêm ambas as palavras)
```

**`ts_rank(...)`** — retorna um score de 0 a 1 baseado na frequência e raridade das palavras.

---

## 7. Busca Híbrida — O Melhor dos Dois Mundos

### Por que combinar as duas buscas?

Cada técnica tem pontos cegos:

| Situação | Busca Semântica | BM25 |
|----------|-----------------|------|
| "qual o prazo?" → "tempo limite é 30 dias" | ✅ Encontra (semelhança semântica) | ❌ Não encontra (palavras diferentes) |
| "CPF: 123.456.789-01" → busca por "CPF" | ⚠️ Pode encontrar (depende do embedding) | ✅ Encontra sempre (palavra exata) |
| Siglas internas da empresa ("PPRA", "LTCAT") | ❌ Modelo pode não conhecer | ✅ Encontra exatamente |
| Perguntas vagas ("me conte sobre aquele documento") | ✅ Melhor chance | ❌ Pouco útil |

A combinação é mais robusta que qualquer uma sozinha.

### O SQL da Busca Híbrida

```java
// PgVectorStoreAdapter.java — findSimilarHybrid()
String sql = """
    SELECT
        c.id, c.document_id, c.content,
        c.embedding::text, c.metadata::text,
        (
            ?  * (1.0 - (c.embedding <=> ?::vector))           -- 85% busca semântica
            + ? * COALESCE(
                    ts_rank(
                        to_tsvector('simple', c.content),       -- índice de palavras
                        plainto_tsquery('simple', ?)            -- palavras da pergunta
                    ), 0.0)                                     -- 15% BM25
        ) AS score
    FROM chunks c
    JOIN documents d ON c.document_id = d.id
    WHERE c.status = 'ACTIVE'
      AND d.status = 'ACTIVE'
      AND d.namespace_id = ?
    ORDER BY (...mesmo score...) DESC
    LIMIT ?
    """;

// Parâmetros passados na ordem:
// semanticWeight (0.85), vectorStr, bm25Weight (0.15), keywords,
// namespaceId,
// semanticWeight, vectorStr, bm25Weight, keywords,
// topK
```

### Por que 85% semântico e 15% BM25?

O `ts_rank` retorna valores tipicamente entre `0.01` e `0.05` — muito menores que o
score vetorial (que fica entre `0.0` e `1.0`). Se usássemos 50%/50%, o BM25 quase não
influenciaria o resultado porque seus números são 10-20x menores.

Com 85%/15%, o BM25 consegue "desempatar" entre chunks com scores semânticos parecidos
quando um deles também contém as palavras exatas da pergunta.

**Exemplo numérico:**

| Chunk | Score Semântico | ts_rank | Score Híbrido |
|-------|----------------|---------|---------------|
| A — "prazo de 30 dias" | 0.78 | 0.04 | `0.85×0.78 + 0.15×0.04 = 0.669` |
| B — "tempo máximo" | 0.76 | 0.001| `0.85×0.76 + 0.15×0.001 = 0.646` |

Chunk A vence por ter tanto a semelhança semântica quanto a palavra "prazo" no texto.

### Fallback automático

```java
// QueryChatService.java
private List<ScoredChunk> fetchScored(..., RagConfig cfg) {
    if (cfg.hybridEnabled()) {
        try {
            return vectorStorePort.findSimilarHybrid(...);
        } catch (Exception e) {
            // Se falhar (pg sem extensão, timeout, etc.) → cai na busca simples
            log.warn("Busca híbrida falhou, usando vetorial pura: {}", e.getMessage());
        }
    }
    return vectorStorePort.findSimilar(...);
}
```

Se o banco não tiver `to_tsvector` disponível (alguns ambientes mínimos), o sistema
continua funcionando com a busca semântica pura — sem travar.

---

## 8. Re-ranking — Filtrando o Que Não Serve

### O Problema dos Chunks Curtos

A busca retorna os `topK × 3` melhores candidatos (ex: se topK=3, busca 9 chunks).
Alguns desses 9 podem ser fragmentos muito curtos que tiveram alta similaridade mas
não têm informação suficiente:

```
Chunk: "Sim."    → score 0.85 (alta similaridade vetorial com "pode fazer?")
Chunk: "Ok."     → score 0.82
Chunk: "Conforme mencionado anteriormente, o prazo máximo..." → score 0.79
```

Os dois primeiros são inúteis para a IA responder. O re-ranking os penaliza:

```java
// domain/entity/ScoredChunk.java
public double adjustedScore(int minLength) {
    if (chunk.content().length() < minLength) {  // minLength = 80 chars (configurável)
        return score * 0.85;    // penalidade de 15%
    }
    return score;
}
```

```
"Sim."   (4 chars < 80) → 0.85 × 0.85 = 0.72  ← cai para baixo do minScore 0.10? Não neste caso,
                                                    mas fica atrás do chunk informativo
"Ok."    (3 chars < 80) → 0.82 × 0.85 = 0.70
"Conforme..." (42 chars > 80) → 0.79 × 1.00 = 0.79  ← sobe na classificação
```

### O Pipeline Completo do Re-ranking

```java
// QueryChatService.java — retrieveAndRerank()
private List<Chunk> retrieveAndRerank(..., RagConfig cfg) {
    int fetchK = cfg.topK() * 3;  // busca 3x mais para ter margem de descarte

    // 1. Busca os candidatos (híbrida ou vetorial)
    List<ScoredChunk> scored = fetchScored(queryEmbedding, question, fetchK, namespaceId, cfg);

    // 2. Aplica penalidade em chunks curtos + filtra abaixo do minScore + ordena + limita
    return scored.stream()
            .filter(sc -> sc.adjustedScore(cfg.shortChunkLength()) >= cfg.minScore())
            .sorted(Comparator.comparingDouble(
                    (ScoredChunk sc) -> sc.adjustedScore(cfg.shortChunkLength())).reversed())
            .limit(cfg.topK())     // retorna apenas os 3 melhores (configurável)
            .map(ScoredChunk::chunk)
            .collect(Collectors.toList());
}
```

**Parâmetros configuráveis pelo admin (sem reiniciar o servidor):**
- `topK = 3` → quantos chunks enviar para a IA
- `minScore = 0.10` → score mínimo para incluir um chunk
- `shortChunkLength = 80` → quantos caracteres para considerar um chunk "curto"

---

## 9. Como a LLM Recebe e Usa o Contexto

### O que é enviado para a IA

A IA não recebe o banco de dados inteiro. Ela recebe uma única chamada com:

```
[System Prompt]
Você é um especialista operacional. Use EXCLUSIVAMENTE as informações do contexto.
Nunca mencione nomes de arquivos. Responda em português do Brasil...

[Contexto — os chunks selecionados, com PII mascarada]
[Fonte: manual_segurança.pdf]
O procedimento em caso de acidente de trabalho é: 1) comunicar ao supervisor...

---

[Fonte: politicas_rh.pdf]
Em situações de emergência, o responsável deve contatar [TELEFONE] imediatamente...

[Histórico da conversa — últimas 6 mensagens]
Usuário: como procedo em caso de acidente?
Assistente: Em caso de acidente, você deve...

[Mensagem atual]
Usuário: e quem eu chamo fora do horário?
```

### O System Prompt — A "Personalidade" da IA

```java
// QueryChatService.java
private static final String SYSTEM_PROMPT = """
        Você é um especialista operacional sênior com conhecimento completo sobre os
        processos, regras e procedimentos da organização.

        1. USE EXCLUSIVAMENTE as informações fornecidas no contexto abaixo.
           Nunca invente, suponha ou extrapole além do que está ali.

        2. ENTREGUE a informação de forma direta e autoritativa —
           como se você mesmo fosse o autor do conteúdo.

        3. NUNCA mencione nomes de arquivos, fontes ou metadados.

        5. SE a informação não estiver disponível no contexto:
           responda apenas: "Não tenho essa informação disponível no momento."

        6. TOKENS como [CPF], [EMAIL], [TELEFONE] são dados protegidos pela LGPD.
           Nunca tente deduzi-los.

        7. Responda sempre em português do Brasil.
        """;
```

**Por que a regra 1 é tão importante?**  
Sem ela, a IA pode "alucinar" — inventar respostas plausíveis mas falsas, misturando
conhecimento geral de treinamento com os documentos da empresa. Com a regra, a IA
só fala o que está nos documentos.

### O PII Masking — Proteção Antes de Enviar

**Antes** de enviar o contexto para a API do Groq (ou qualquer LLM externo), o sistema
mascara dados pessoais:

```java
// QueryChatService.java
String context = relevantChunks.stream()
        .map(c -> {
            PiiMaskingService.MaskResult masked = piiMaskingService.maskWithStats(c.content());
            return String.format("[Fonte: %s]\n%s", fileName, masked.maskedText());
        })
        .collect(Collectors.joining("\n\n---\n\n"));
```

```
ANTES (no banco):       "CPF: 123.456.789-01, telefone (11) 98765-4321"
DEPOIS (enviado à IA):  "CPF: [CPF], telefone [TELEFONE]"
```

O banco guarda o texto original (necessário para a busca semântica funcionar).
Somente o que sai pela API é mascarado — exigência da LGPD.

### Como a LLM "Entende" Sem Banco de Dados

A LLM não tem acesso direto ao PostgreSQL. Ela recebe apenas texto puro. O "entendimento"
dela vem de dois fatores:

1. **Treinamento:** O modelo foi treinado em bilhões de textos e aprendeu português,
   regras gramaticais, raciocínio lógico, e como responder perguntas.

2. **Contexto fornecido:** O RAG injeta o conhecimento específico da empresa no prompt.
   A IA usa esse contexto como se fosse sua própria memória para aquela conversa.

```
LLM sem contexto: "Não conheço os procedimentos desta empresa."
LLM com contexto: "Em caso de acidente de trabalho, você deve imediatamente comunicar
                   ao seu supervisor e preencher o formulário CAT dentro de 24h."
                   (porque o sistema injetou esse texto no contexto)
```

### O Histórico de Conversa — Contexto Multi-turno

```java
// QueryChatService.java
List<ChatMessage> conversation = buildConversation(dbHistory, question);
String answer = llmPort.complete(SYSTEM_PROMPT + "\n\nCONTEXTO:\n" + context, conversation);
```

O histórico permite perguntas de acompanhamento:

```
Turno 1 — Usuário: "Como funciona o processo de férias?"
Turno 1 — IA: "As férias devem ser solicitadas com 30 dias de antecedência..."

Turno 2 — Usuário: "E se eu precisar cancelar?"  ← sem "férias" na pergunta
```

Sem histórico, a IA não saberia que "cancelar" se refere a férias.
Com histórico, ela tem contexto das últimas 6 mensagens.

### A Interface LlmPort — Trocar o Modelo sem Mudar o Código

```java
// application/port/out/LlmPort.java
public interface LlmPort {
    String complete(String systemPrompt, List<ChatMessage> history);
}
```

Esta interface de uma linha é o que permite usar Groq, a LLM interna da empresa, ou
qualquer outro provedor. O `QueryChatService` só chama `llmPort.complete(...)` —
sem saber qual modelo está por trás.

---

## 10. O Pipeline Completo — Do Upload à Resposta

### Fase 1 — Ingestão (acontece uma vez por documento)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         INGESTÃO                                    │
│                                                                     │
│  1. Usuário faz upload do PDF/CSV                                   │
│     POST /api/documents (multipart/form-data)                       │
│                                                                     │
│  2. IngestDocumentService                                           │
│     ├── Verifica se há versão anterior → supersede (versionamento)  │
│     ├── Salva arquivo em disco                                      │
│     └── Registra no banco: status = PROCESSING                      │
│                  ↓ HTTP 202 retorna imediatamente                   │
│                                                                     │
│  3. AsyncIngestWorker (em background, outra thread)                 │
│     ├── Lê arquivo do disco                                         │
│     ├── Parser PDF/CSV → extrai texto puro                          │
│     ├── ChunkingStrategy.split() → divide em pedaços de 300 palavras│
│     │   com sobreposição de 50 palavras                             │
│     │                                                               │
│     │   Para cada chunk:                                            │
│     ├── GoogleEmbeddingAdapter.embed() → 3072 floats               │
│     │   (chamada à API do Google por chunk)                         │
│     ├── PgVectorStoreAdapter.save() → INSERT no PostgreSQL          │
│     │                                                               │
│     └── updateProcessingStatus("READY") ou ("FAILED")              │
│                                                                     │
│  4. Frontend faz polling GET /api/documents/{id}/status             │
│     até receber READY ou FAILED                                     │
└─────────────────────────────────────────────────────────────────────┘
```

### Fase 2 — Consulta (acontece a cada pergunta)

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CONSULTA                                    │
│                                                                     │
│  1. Usuário digita pergunta no chat                                 │
│     POST /api/chat { question, sessionId, namespaceId }             │
│                          ↓                                          │
│  2. RateLimitFilter → bloqueia se excedeu requisições              │
│                          ↓                                          │
│  3. JwtAuthenticationFilter → valida token JWT                      │
│                          ↓                                          │
│  4. ChatController → QueryChatService.queryWithSession()            │
│                          ↓                                          │
│  5. TokenUsageService.checkLimit() → bloqueia se excedeu quota      │
│                          ↓                                          │
│  6. GoogleEmbeddingAdapter.embed(question)                          │
│     → vetor da pergunta [0.1234, -0.5678, ..., 0.6789]             │
│                          ↓                                          │
│  7. PgVectorStoreAdapter.findSimilarHybrid()                        │
│     → busca os 9 chunks mais próximos (topK×3)                      │
│       usando 85% semântico + 15% BM25                               │
│                          ↓                                          │
│  8. Re-ranking                                                      │
│     → ajustedScore() (penaliza chunks curtos)                       │
│     → filtra abaixo de minScore=0.10                                │
│     → retorna top-3 chunks                                          │
│                          ↓                                          │
│  9. PiiMaskingService.maskWithStats()                               │
│     → substitui CPF, email, telefone por [CPF], [EMAIL]...          │
│                          ↓                                          │
│  10. GroqLlmAdapter.complete(systemPrompt + contexto, histórico)    │
│      → chamada à API do Groq (LLM) → resposta em texto              │
│                          ↓                                          │
│  11. Persiste histórico, registra observabilidade e tokens          │
│                          ↓                                          │
│  12. ChatController retorna HTTP 200                                │
│      { answer, interactionId, sessionId, sources }                  │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 11. Troca de Provedores — LLM e Embedding da Empresa

### Como o Sistema foi Projetado para Trocar de Provedor

O projeto tem dois pontos de troca independentes:

| Componente | Interface | Implementações disponíveis |
|------------|-----------|---------------------------|
| LLM (gera respostas) | `LlmPort` | `GroqLlmAdapter`, `CopilotLlmAdapter`, `GoogleLlmAdapter` |
| Embedding (gera vetores) | `EmbeddingPort` | `GoogleEmbeddingAdapter`, `CopilotEmbeddingAdapter` |

Para trocar, basta mudar **uma linha** no `application.yml`:

```yaml
ragbot:
  ai:
    provider: groq       # atual: Groq como LLM + Google como embedding
#   provider: copilot    # para trocar para GitHub Copilot/Azure OpenAI
```

### Usando a LLM Interna da Empresa (Self-hosted)

Se a empresa tem uma LLM rodando no próprio servidor (ex: Ollama, vLLM, LM Studio, ou
um endpoint OpenAI-compatible), a integração é **trivial** — basta criar um novo adapter:

```java
// adapter/out/ai/interna/LlmInternaAdapter.java

@Component
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "interna")
public class LlmInternaAdapter implements LlmPort {

    private final WebClient webClient;
    private final String model;

    public LlmInternaAdapter(
            @Value("${llm.interna.endpoint}") String endpoint,   // ex: http://servidor-ia:11434
            @Value("${llm.interna.model}") String model,         // ex: llama3.2:70b
            @Value("${llm.interna.token:}") String token) {      // pode ser vazio se sem autenticação

        WebClient.Builder builder = WebClient.builder().baseUrl(endpoint);
        if (!token.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + token);
        }
        this.webClient = builder.build();
        this.model = model;
    }

    @Override
    public String complete(String systemPrompt, List<ChatMessage> history) {
        // A maioria das LLMs self-hosted usa a mesma API do OpenAI (formato compatível)
        // ex: Ollama, vLLM, LM Studio, LocalAI — todos aceitam este formato
        List<MessageDto> messages = new ArrayList<>();
        messages.add(new MessageDto("system", systemPrompt));
        history.forEach(m -> messages.add(new MessageDto(m.role(), m.content())));

        ChatResponseDto response = webClient.post()
                .uri("/v1/chat/completions")   // endpoint padrão OpenAI-compatible
                .bodyValue(new ChatRequestDto(model, messages, 2000, 0.3))
                .retrieve()
                .bodyToMono(ChatResponseDto.class)
                .block();

        return response.choices().get(0).message().content();
    }

    // Os mesmos DTOs do GroqLlmAdapter — estrutura OpenAI é padrão
    record MessageDto(String role, String content) {}
    record ChatRequestDto(String model, List<MessageDto> messages,
                          @JsonProperty("max_tokens") int maxTokens, double temperature) {}
    record ChatResponseDto(List<ChoiceDto> choices) {}
    record ChoiceDto(MessageDto message) {}
}
```

E no `application.yml`:

```yaml
ragbot:
  ai:
    provider: interna

llm:
  interna:
    endpoint: http://servidor-ia.empresa.local:11434
    model: llama3.2:70b
    token: ${LLM_INTERNA_KEY:}   # opcional
```

**Isso é tudo.** Nenhuma outra mudança no código.

### E se a LLM Interna Não for OpenAI-Compatible?

Se a API da empresa usa um formato diferente (ex: retorna XML, tem autenticação proprietária),
o adapter precisa adaptar o formato. A estrutura do código é a mesma — só muda o payload.
Isso leva tipicamente 1-2 horas para um desenvolvedor implementar.

### Usando Embedding Interno (Modelo de Embedding Próprio)

Se a empresa tiver um modelo de embedding próprio (ex: para domínio jurídico, médico):

```java
// adapter/out/ai/interna/EmbeddingInternoAdapter.java

@Component
@ConditionalOnProperty(name = "ragbot.ai.provider", havingValue = "interna")
public class EmbeddingInternoAdapter implements EmbeddingPort {

    @Override
    public List<Float> embed(String text) {
        // Chama o endpoint de embedding interno
        // Retorna lista de floats (o tamanho deve ser consistente com o banco)
    }
}
```

**Atenção importante:** Se você trocar o modelo de embedding, **todos os documentos
precisam ser reingestados**. Vetores de modelos diferentes não são comparáveis —
é como misturar coordenadas GPS em graus com coordenadas em radianos.

O projeto já tem a migration V2 como exemplo de como fazer isso:

```sql
-- V2__upgrade_embedding_dimension_to_3072.sql
TRUNCATE chunks;   -- apaga todos os chunks (vetores incompatíveis)
ALTER TABLE chunks ALTER COLUMN embedding TYPE vector(3072);  -- muda a dimensão
```

---

## 12. Deploy Local com Banco de Dados Próprio

### Arquitetura Recomendada para Produção Interna

```
┌──────────────────────────────────────────────────────────────────┐
│                    SERVIDOR DA EMPRESA                           │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │  Docker (ou Kubernetes)                                 │    │
│  │                                                         │    │
│  │  ┌────────────────┐   ┌──────────────────────────────┐ │    │
│  │  │  Spring Boot   │   │  PostgreSQL + pgvector        │ │    │
│  │  │  (porta 8080)  │◄──│  (porta 5432, INTERNO)        │ │    │
│  │  │                │   │                              │ │    │
│  │  │  Lê:           │   │  Tabelas: documents, chunks,  │ │    │
│  │  │  - documentos  │   │  users, chat_sessions...      │ │    │
│  │  │  - embeddings  │   │                              │ │    │
│  │  └────────┬───────┘   └──────────────────────────────┘ │    │
│  │           │                                             │    │
│  │  ┌────────▼───────┐                                     │    │
│  │  │  Nginx         │                                     │    │
│  │  │  (porta 443)   │                                     │    │
│  │  └────────────────┘                                     │    │
│  └─────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────┘
             │                                  │
             ▼ HTTPS                            ▼ HTTPS
    API Google Gemini                   LLM da Empresa
    (só para embedding)                 (servidor interno)
    Custo: ~$0.00002/1000 chars        Custo: zero
```

### O que sai da empresa e o que fica interno

| Dado | Fica na empresa? | Vai para fora? |
|------|-----------------|----------------|
| Documentos originais | ✅ PostgreSQL interno | ❌ Nunca |
| Texto dos chunks | ✅ PostgreSQL interno | ❌ Nunca |
| Perguntas dos usuários | ✅ chat_history no banco | ✅ Para embedding (sem PII) |
| Texto enviado para embedding | ✅ Banco guarda original | ✅ Google (sem PII — mascarado) |
| Texto enviado para LLM | — | ✅ LLM interna (sem sair) |
| Respostas da IA | ✅ chat_history | — |

**Se a empresa usar embedding interno também**, nada sai da empresa.

### Configuração para Deploy Local

**1. Clonar e configurar variáveis:**

```bash
# No servidor Linux (ou WSL)
git clone https://github.com/AbnerMoura15/RagBot.git
cd RagBot/ragbot_fase4

# Arquivo de variáveis de ambiente (NÃO commitado no Git)
cp .env.example .env
```

```env
# .env (editar com os valores reais)
POSTGRES_PASSWORD=sua_senha_segura_aqui
JWT_SECRET=minimo-32-caracteres-aqui-seguro!!
GOOGLE_API_KEY=AIza...                    # para embedding
LLM_INTERNA_KEY=chave-da-llm-interna     # se a LLM pedir autenticação
```

**2. Subir tudo com Docker Compose:**

```bash
# Ambiente de produção local
docker compose -f docker-compose.prod.yml up -d

# Verificar se subiu:
docker compose ps
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

**3. Acessar:**
- Frontend: `http://servidor.empresa.local:8080`
- Login padrão: `admin` / `Admin@123` (trocar no primeiro acesso)

### Instalar o pgvector no PostgreSQL da Empresa

Se a empresa já tem um PostgreSQL, o pgvector pode ser instalado como extensão:

```bash
# Ubuntu/Debian
apt install postgresql-16-pgvector

# CentOS/RHEL
yum install pgvector_16

# Após instalar, rodar no banco:
CREATE EXTENSION IF NOT EXISTS vector;
```

Versões suportadas: PostgreSQL 12 a 17. Verifique em:
[pgvector no GitHub](https://github.com/pgvector/pgvector)

---

## 13. Perguntas Frequentes — Railway, Deploy e Supabase

---

### ❓ Se fizer Pull Request na master, o Railway faz deploy automaticamente?

**Depende de como o Railway está configurado.** O comportamento padrão do Railway é:

> **Sim, se o repositório GitHub estiver conectado ao projeto Railway e o branch monitorado for `main` (ou `master`), qualquer push/merge nesse branch dispara um deploy automático.**

#### Como verificar e configurar no Railway:

1. Acesse `railway.app` → seu projeto → **Settings**
2. Em **Service** → **Source** → você verá qual branch está sendo monitorado
3. Se estiver configurado como `main` → qualquer PR merged no `main` = deploy automático

#### O fluxo completo:

```
Você cria PR → Code review → Merge na main
                                   ↓
               GitHub notifica o Railway (webhook)
                                   ↓
               Railway detecta novo commit na main
                                   ↓
               Railway clona o repositório
                                   ↓
               Executa o Dockerfile (build multi-stage)
                                   ↓
               Sobe o novo container
                                   ↓
               Verifica /actuator/health
                                   ↓
               Se saudável: troca o container antigo pelo novo (zero downtime)
               Se falhar: mantém o container antigo rodando + alerta
```

#### Tempo de deploy:
- **Build completo** (primeira vez ou mudança no pom.xml): ~5-8 minutos
- **Build com cache** (só mudança no código Java): ~2-3 minutos
- O cache de layers do Docker é o motivo da diferença

---

### ❓ Como funciona o deploy detalhadamente?

#### O Dockerfile é a receita do deploy

```dockerfile
# ESTÁGIO 1: Compilar o Java
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -q    # ← Railway cacheia esta camada

COPY src/ src/
RUN mvn package -DskipTests -q      # compila o .jar

# Extrai camadas do Spring Boot para otimizar rebuild
RUN java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ESTÁGIO 2: Imagem de produção (sem Maven, sem JDK — só JRE)
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S ragbot && adduser -S ragbot -G ragbot
USER ragbot

EXPOSE 8080

# Parâmetros de JVM para containers com 512 MB de RAM
ENTRYPOINT ["sh", "-c",
    "exec java \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=60.0 \
    -XX:+UseSerialGC \
    -Xss256k \
    org.springframework.boot.loader.launch.JarLauncher"]
```

#### O que o Railway faz passo a passo:

```
1. CLONE
   Railway baixa o código do GitHub

2. BUILD
   docker build -t ragbot:latest .
   (executa o Dockerfile)

3. CONFIGURAÇÃO
   Injeta as variáveis de ambiente configuradas no dashboard:
   GROQ_API_KEY, GOOGLE_API_KEY, JWT_SECRET, SPRING_DATASOURCE_URL...

4. INICIALIZAÇÃO DO SPRING BOOT
   O .jar sobe → Spring conecta ao Supabase → Flyway verifica migrations
   → Se houver migration nova (ex: V16), ela é aplicada AUTOMATICAMENTE

5. HEALTH CHECK
   Railway testa GET /actuator/health a cada 30s
   Aguarda status "UP" (Spring Boot leva ~60s para iniciar)

6. SWAP
   Se saudável: container novo substitui o antigo
   O volume /data/documents é preservado (arquivos não são perdidos)

7. MONITORAMENTO
   railway logs --follow   ← ver logs em tempo real
```

#### Comando útil para forçar redeploy sem mudar código:

```bash
railway redeploy   # força novo build com o mesmo código
```

---

### ❓ Como acessar o banco do Supabase para rodar `SELECT * FROM ...`?

Você tem **três opções**, da mais simples à mais técnica:

---

#### Opção 1 — Supabase Table Editor (mais simples, sem instalar nada)

1. Acesse [supabase.com/dashboard](https://supabase.com/dashboard)
2. Selecione seu projeto `ragbot-db`
3. No menu lateral: **Table Editor**
4. Você vê todas as tabelas visualmente — clique em qualquer uma para ver os dados
5. Para queries customizadas: **SQL Editor** (menu lateral) → escreva o SELECT

```sql
-- Exemplos para rodar no Supabase SQL Editor:

-- Ver todos os documentos indexados
SELECT id, file_name, processing_status, ingested_at, namespace_id
FROM documents
WHERE status = 'ACTIVE'
ORDER BY ingested_at DESC;

-- Contar chunks por documento
SELECT d.file_name, COUNT(c.id) as total_chunks
FROM documents d
JOIN chunks c ON c.document_id = d.id
WHERE c.status = 'ACTIVE'
GROUP BY d.file_name
ORDER BY total_chunks DESC;

-- Ver histórico de conversas
SELECT cs.username, cm.role, cm.content, cm.created_at
FROM chat_sessions cs
JOIN chat_messages cm ON cm.session_id = cs.id
ORDER BY cm.created_at DESC
LIMIT 50;

-- Ver uso de tokens por usuário (mês atual)
SELECT username, tokens_used, year_month
FROM user_token_usage
ORDER BY tokens_used DESC;

-- Ver audit log (quem fez o quê)
SELECT actor, action, target_type, detail, ip_address, created_at
FROM audit_log
ORDER BY created_at DESC
LIMIT 100;
```

---

#### Opção 2 — DBeaver com conexão ao Supabase (recomendado para uso frequente)

DBeaver é uma ferramenta gratuita de banco de dados. Conectar ao Supabase:

1. Baixe o DBeaver: [dbeaver.io](https://dbeaver.io)
2. No Supabase: **Settings** → **Database** → **Connection string**
3. Copie os dados de conexão (seção "Direct connection"):

```
Host:     db.[seu-projeto-ref].supabase.co
Port:     5432
Database: postgres
User:     postgres
Password: [sua senha do projeto]
```

4. No DBeaver: **New Connection** → PostgreSQL → cole os dados acima
5. Em **SSL**: marcar "Use SSL" → modo "Require"

Agora você tem acesso completo ao banco igual ao DBeaver local.

**Nota:** Use "Direct connection" (porta 5432) para o DBeaver — não o Transaction Pooler
(6543) que é usado pela aplicação. O pooler tem limitações para queries interativas.

---

#### Opção 3 — psql na linha de comando

```bash
# Instalar psql (se não tiver):
# Ubuntu: apt install postgresql-client
# Mac: brew install postgresql
# Windows: instala junto com o PostgreSQL

# Conectar:
psql "postgresql://postgres:[senha]@db.[ref].supabase.co:5432/postgres?sslmode=require"

# Após conectar:
\dt                              -- listar tabelas
\d chunks                        -- ver estrutura da tabela chunks
SELECT COUNT(*) FROM chunks;     -- contar chunks
\q                               -- sair
```

---

#### Queries úteis para o dia a dia

```sql
-- ── Saúde do sistema ─────────────────────────────────────────────────────────

-- Quantos documentos e chunks ativos por namespace
SELECT
    n.display_name AS namespace,
    COUNT(DISTINCT d.id) AS documentos,
    COUNT(c.id) AS chunks_totais
FROM namespaces n
LEFT JOIN documents d ON d.namespace_id = n.id AND d.status = 'ACTIVE'
LEFT JOIN chunks c ON c.document_id = d.id AND c.status = 'ACTIVE'
GROUP BY n.display_name;

-- Documentos que falharam no processamento
SELECT id, file_name, ingested_at, processing_status
FROM documents
WHERE processing_status = 'FAILED'
ORDER BY ingested_at DESC;

-- ── Uso e performance ────────────────────────────────────────────────────────

-- Top 10 usuários por tokens consumidos no mês atual
SELECT username, tokens_used
FROM user_token_usage
WHERE year_month = EXTRACT(YEAR FROM NOW())::int * 100 + EXTRACT(MONTH FROM NOW())::int
ORDER BY tokens_used DESC
LIMIT 10;

-- Perguntas mais recentes dos usuários
SELECT
    cs.username,
    cm.content AS pergunta,
    cm.created_at
FROM chat_messages cm
JOIN chat_sessions cs ON cs.id = cm.session_id
WHERE cm.role = 'user'
ORDER BY cm.created_at DESC
LIMIT 20;

-- Qualidade das respostas (feedback)
SELECT
    ri.question,
    ri.answer,
    f.rating,      -- 'positive' ou 'negative'
    f.created_at
FROM feedback f
JOIN rag_interactions ri ON ri.id = f.interaction_id
ORDER BY f.created_at DESC;

-- ── Administração ─────────────────────────────────────────────────────────────

-- Listar usuários ativos
SELECT username, email, role, active, last_login
FROM users
ORDER BY last_login DESC NULLS LAST;

-- Ver configuração atual do RAG
SELECT key, value FROM rag_config ORDER BY key;
```

---

## Glossário Rápido — Conceitos de IA

| Termo | Significado em linguagem simples |
|-------|----------------------------------|
| **Embedding** | Código numérico (vetor) que representa o significado de um texto |
| **Vetor** | Lista de números. Ex: `[0.12, -0.56, 0.89, ...]` com 3072 números |
| **Distância de Cosseno** | Mede o ângulo entre dois vetores: 0 = iguais, 1 = opostos |
| **Score de Similaridade** | `1 - distância`. Maior = mais parecido |
| **LLM** | Large Language Model. O modelo de IA que gera o texto de resposta |
| **Context Window** | Limite de texto que a LLM consegue processar de uma vez |
| **System Prompt** | Instruções fixas que definem como a IA deve se comportar |
| **BM25** | Algoritmo de busca por palavras-chave (como o Google antigo usava) |
| **Busca Híbrida** | Combina busca semântica (significado) + BM25 (palavras exatas) |
| **Re-ranking** | Reordenar/filtrar resultados da busca por critérios adicionais |
| **Chunk** | Pedaço de texto de um documento (ex: 300 palavras) |
| **Chunking** | Processo de dividir um documento em chunks |
| **pgvector** | Extensão do PostgreSQL que permite guardar e buscar vetores |
| **IVFFLAT** | Índice vetorial: organiza vetores em grupos para busca rápida |
| **GIN** | Índice do PostgreSQL para busca em texto completo (full-text search) |
| **RAG** | Retrieval-Augmented Generation: busca documentos + IA responde |
| **Namespace** | Agrupamento de documentos (ex: por departamento ou área) |
| **PII** | Dados pessoais: CPF, e-mail, telefone, CNPJ... |
| **LGPD** | Lei Geral de Proteção de Dados. Exige mascarar PII antes de enviar a terceiros |
| **Fire-and-forget** | Executar uma operação sem esperar o resultado (ex: registrar tokens) |
| **Context Window Overflow** | Quando o prompt é maior que o limite da LLM aceitar |
| **Alucinação** | Quando a IA inventa informações plausíveis mas falsas |
| **Temperature** | Controla a criatividade da IA. 0 = determinístico, 1 = criativo |
| **Token** | Unidade de texto para a LLM. ~0.75 palavras em inglês, ~0.6 em português |

---

*Documento gerado em 2026-05-12 — Crivo AI (RAGBot)*  
*Para dúvidas técnicas: consulte a documentação de estudo `DOCUMENTACAO_ESTUDO.md`*
