# Documentação: Tela de Configuração IA (Config IA)

> **Para quem é este documento?**
> Desenvolvedor iniciante que quer entender o que cada parâmetro da tela faz,
> por que ele existe, e o que acontece quando você aumenta ou diminui o valor.

---

## Índice

1. [Conceitos fundamentais: o que é um Chunk?](#1-conceitos-fundamentais-o-que-é-um-chunk)
2. [Conceitos fundamentais: o que é um Token?](#2-conceitos-fundamentais-o-que-é-um-token)
3. [Como a tela de configuração funciona tecnicamente](#3-como-a-tela-de-configuração-funciona-tecnicamente)
4. [Parâmetro: Top-K (chunks recuperados)](#4-parâmetro-top-k-chunks-recuperados)
5. [Parâmetro: Score mínimo](#5-parâmetro-score-mínimo)
6. [Parâmetro: Comprimento mínimo do chunk](#6-parâmetro-comprimento-mínimo-do-chunk)
7. [Parâmetro: Busca híbrida (semântica + BM25)](#7-parâmetro-busca-híbrida-semântica--bm25)
8. [Parâmetro: Peso semântico](#8-parâmetro-peso-semântico)
9. [Parâmetro: Limite mensal de tokens](#9-parâmetro-limite-mensal-de-tokens)
10. [Parâmetro: Histórico de Chat (mensagens)](#10-parâmetro-histórico-de-chat-mensagens)
11. [Tabela rápida de referência](#11-tabela-rápida-de-referência)
12. [Cenários práticos: quando mudar cada parâmetro](#12-cenários-práticos-quando-mudar-cada-parâmetro)
13. [Como as mudanças chegam ao backend (sem redeploy)](#13-como-as-mudanças-chegam-ao-backend-sem-redeploy)
14. [Quem pode alterar a configuração?](#14-quem-pode-alterar-a-configuração)

---

## 1. Conceitos fundamentais: o que é um Chunk?

### A analogia do livro

Imagine que você subiu um manual de 400 páginas para o sistema. A LLM não pode ler as 400 páginas de uma vez — ela tem um limite de "memória de curto prazo" (a janela de contexto). Além disso, enviar 400 páginas para a API do Groq ou do Google custaria muito em tokens.

A solução é o **chunking**: ao ingerir o documento, o sistema o divide em pedaços menores chamados **chunks**.

```
Documento completo (400 páginas)
          ↓
   Chunking automático
          ↓
 [Chunk 1] [Chunk 2] [Chunk 3] ... [Chunk N]
  300 pal.  300 pal.  300 pal.      300 pal.
```

Cada chunk é um trecho de texto com **300 palavras** (padrão configurado no `application.yml`), com uma sobreposição de **50 palavras** entre chunks consecutivos.

### Por que a sobreposição (overlap)?

```
Parágrafo real no documento:
"...a regra de compliance exige aprovação do comitê. O comitê
se reúne toda segunda-feira. A ata deve ser assinada..."

Sem overlap:
  Chunk 1: "...a regra de compliance exige aprovação do comitê."
  Chunk 2: "O comitê se reúne toda segunda-feira. A ata deve..."

Com overlap de 50 palavras:
  Chunk 1: "...a regra de compliance exige aprovação do comitê. O comitê
            se reúne toda segunda-feira."           ← últimas 50 palavras repetidas
  Chunk 2: "O comitê se reúne toda segunda-feira. A ata deve ser assinada..."
```

Sem overlap, uma frase que cruza a fronteira entre dois chunks seria cortada ao meio e nenhum chunk teria o contexto completo. Com overlap, a informação de transição aparece em ambos os chunks.

### O que o sistema faz com cada chunk?

```
Texto do chunk
     ↓
Embedding (Google Gemini) → vetor de 3072 números
     ↓
INSERT no banco (PostgreSQL + pgvector)
  - content    → o texto original
  - embedding  → vetor [0.12, -0.87, 0.03, ...]  (3072 floats)
  - tsvector   → índice BM25 (palavras-chave)
  - metadata   → source, namespace, chunkIndex, totalChunks
```

Quando o usuário faz uma pergunta, o sistema **não lê todos os chunks** — ele encontra os chunks mais relevantes usando a distância vetorial (busca semântica) e/ou palavras-chave (BM25).

### Onde o código define o tamanho do chunk?

```yaml
# application.yml
ragbot:
  chunking:
    chunk-size: 300   # palavras por chunk
    overlap: 50       # palavras de sobreposição
```

```java
// ChunkingStrategy.java — lógica do sliding window
int start = 0;
while (start < words.length) {
    int end = Math.min(start + chunkSize, words.length);
    String chunk = String.join(" ", Arrays.copyOfRange(words, start, end));
    chunks.add(chunk);
    start = end - overlap;   // volta 50 palavras → cria a sobreposição
}
```

---

## 2. Conceitos fundamentais: o que é um Token?

### Token não é palavra

No mundo dos LLMs, **token** é a unidade mínima de texto que o modelo processa. Ele não corresponde exatamente a uma palavra nem a um caractere — é algo intermediário:

| Texto         | Tokens (aproximado) |
|---------------|---------------------|
| "hello"       | 1 token             |
| "unhappy"     | 2 tokens (un + happy)|
| "RagBot"      | 2–3 tokens          |
| "configuração"| 3–4 tokens          |
| " " (espaço)  | parte do token anterior |

**Regra prática:** 1 palavra em português ≈ **1,3 a 1,5 tokens**.

### Por que tokens importam?

As APIs de LLM (Groq, Google, OpenAI) cobram por tokens processados. Cada chamada ao LLM envolve:

```
[System Prompt] + [Histórico do chat] + [Chunks recuperados] + [Pergunta do usuário]
       ↓                   ↓                     ↓                      ↓
  ~500 tokens         ~300 tokens          ~1.500 tokens           ~50 tokens
  (fixo)              (variável)           (variável)              (variável)
                                           
Total médio por pergunta: ~2.350 tokens de entrada
+ Resposta gerada:        ~300–800 tokens de saída
= ~2.650–3.150 tokens consumidos por pergunta
```

Se o sistema tem 50 usuários fazendo 20 perguntas por dia, são ~3.000.000 tokens/mês — o que pode gerar custo significativo ou atingir cotas gratuitas.

### Como o sistema estima os tokens consumidos?

O sistema não chama a API só para contar tokens (isso seria desperdício). Ele usa uma **heurística**:

```java
// TokenUsageService.java
private long estimate(String text) {
    if (text == null || text.isBlank()) return 0;
    return (long) (text.split("\\s+").length * 1.3);
    //                   ^^^^^^^^^^^^^^^^^            ^^^^^
    //                   número de palavras           fator de conversão
}
```

Divide o texto por espaços → conta as palavras → multiplica por 1,3 (cada palavra gera ~1,3 tokens em média).

### Onde os tokens ficam registrados?

Na tabela `token_usage` do banco de dados:

```sql
-- Exemplo de dados na tabela
SELECT * FROM token_usage WHERE username = 'maria';

-- username | year_month | tokens_used
-- maria    | 202605     | 45200
-- maria    | 202604     | 38700
```

O campo `year_month` usa o formato `AAAAMM` (ex: maio de 2026 = `202605`), calculado como:

```java
private int currentYearMonth() {
    LocalDate now = LocalDate.now();
    return now.getYear() * 100 + now.getMonthValue();
    //     2026 * 100 + 5  =  202605
}
```

---

## 3. Como a tela de configuração funciona tecnicamente

### O fluxo completo ao salvar

```
Admin clica em "Salvar configuração"
        ↓
saveRagConfig() no JavaScript (index.html:1823)
  → lê todos os inputs
  → fetch PUT /api/admin/config  +  Bearer token JWT
        ↓
RagConfigController.updateConfig() (backend)
  → valida cada campo com Bean Validation (@Min, @Max, @DecimalMin, @DecimalMax)
  → cria novo RagConfig (record imutável)
  → chama ragConfigService.updateConfig(updated)
        ↓
RagConfigService.updateConfig()
  → configPort.save(config)  → persiste no banco (tabela rag_config)
  → cachedConfig = config    → atualiza cache em memória
  → cacheTime = Instant.now()
        ↓
AuditLogService.record()  → registra quem mudou o que e quando
        ↓
Retorna HTTP 200 com os valores salvos
        ↓
Toast "Configuração salva!" aparece no frontend
```

### A tabela rag_config no banco

Os valores são armazenados em uma tabela chave/valor no banco:

```sql
SELECT * FROM rag_config;
-- key                  | value
-- top_k                | 3
-- min_score            | 0.10
-- short_chunk_length   | 80
-- hybrid_enabled       | true
-- semantic_weight      | 0.85
-- monthly_token_limit  | 100000
-- max_history_messages | 6
```

### O cache de 30 segundos

A configuração é lida **uma vez a cada 30 segundos** no máximo, não a cada pergunta:

```java
// RagConfigService.java
private static final Duration CACHE_TTL = Duration.ofSeconds(30);

public RagConfig getConfig() {
    if (cachedConfig == null ||
            Duration.between(cacheTime, Instant.now()).compareTo(CACHE_TTL) > 0) {
        cachedConfig = configPort.load();   // só vai ao banco se o cache expirou
        cacheTime    = Instant.now();
    }
    return cachedConfig;
}
```

Quando o admin salva, o cache é atualizado imediatamente (não espera os 30s):

```java
public void updateConfig(RagConfig config) {
    configPort.save(config);
    cachedConfig = config;        // já usa o novo valor
    cacheTime    = Instant.now(); // reseta o timer
}
```

**Por que o cache?** Sem ele, cada pergunta do chat faria uma query ao banco só para ler a configuração — desnecessário, já que ela muda raramente.

### Valores padrão (quando o banco ainda não tem a configuração)

```java
// RagConfig.java
public static RagConfig defaults() {
    return new RagConfig(3, 0.10, 80, true, 0.85, 100_000L, 6);
    //                   ^   ^     ^   ^     ^     ^          ^
    //                topK minScore SC hybr  sem   tokens   hist
}
```

Esses valores são usados em testes automatizados e na primeira execução, antes de o Flyway criar a tabela.

---

## 4. Parâmetro: Top-K (chunks recuperados)

**Campo na tela:** slider de 1 a 20 | **Padrão:** 3

### O que é

Após a busca vetorial/híbrida, o banco retorna os chunks ordenados por relevância. O **Top-K** define **quantos chunks** são enviados ao LLM como contexto da resposta.

```
Busca retorna 20 chunks ordenados por score:
[0.94] Chunk A — regra de férias coletivas
[0.91] Chunk B — cálculo de abono de férias
[0.87] Chunk C — aprovação de férias
[0.71] Chunk D — política de férias parciais
[0.65] Chunk E — ...
...

Com Top-K = 3:   envia apenas [Chunk A, Chunk B, Chunk C] ao LLM
Com Top-K = 5:   envia [Chunk A, Chunk B, Chunk C, Chunk D, Chunk E] ao LLM
```

### O re-ranking acontece antes

Na verdade, internamente o sistema busca `topK * 3` candidatos antes de filtrar:

```java
// QueryChatService.java — método retrieveAndRerank()
int candidates = config.topK() * 3;   // ex: se topK=3, busca 9 candidatos
List<ScoredChunk> raw = fetchScored(question, namespace, candidates, config);
// ... aplica penalidade a chunks curtos ...
// ... filtra por minScore ...
// ... retorna os melhores topK
```

Essa estratégia é chamada de **over-fetch + re-rank**: pesca mais do que precisa para ter liberdade de descartar os piores antes de apresentar ao LLM.

### Efeitos de aumentar o Top-K

| Aspecto | Aumentar (ex: 3 → 8) | Diminuir (ex: 3 → 1) |
|---------|----------------------|----------------------|
| Qualidade da resposta | Pode melhorar (mais contexto) | Pode piorar (contexto limitado) |
| Risco de confusão do LLM | Aumenta (mais texto pode distrair) | Baixo |
| Tokens consumidos | Aumenta | Diminui |
| Velocidade | Levemente mais lento | Levemente mais rápido |
| Custo de API | Aumenta | Diminui |

### Quando aumentar?

- Documentos longos onde a resposta pode estar espalhada em vários trechos
- Perguntas complexas que precisam de múltiplas fontes para ser respondidas
- Domínio muito específico onde cada chunk tem informação complementar

### Quando diminuir?

- Documentos bem estruturados onde a resposta está sempre em um único parágrafo
- Custo de API é preocupação (menos tokens = menos custo)
- Respostas do LLM estão ficando "confusas" com muito contexto

### Limite técnico

O máximo permitido pela validação é 20:

```java
// RagConfigController.java
record RagConfigRequest(
    @Min(1) @Max(20) int topK,   // ← não aceita 0 ou 21
    ...
)
```

---

## 5. Parâmetro: Score mínimo

**Campo na tela:** slider de 0,00 a 1,00 | **Padrão:** 0,10

### O que é

Cada chunk recuperado tem um **score de relevância** entre 0 e 1. O score mínimo é um **filtro de qualidade**: chunks com score abaixo desse valor são **descartados** mesmo que o Top-K ainda não tenha sido preenchido.

```
Score = 1.0 → chunk idêntico à pergunta (raramente acontece)
Score = 0.8 → chunk muito relevante
Score = 0.5 → chunk moderadamente relevante
Score = 0.1 → chunk pouco relacionado (fronteira do padrão)
Score = 0.0 → chunk completamente diferente
```

### Como o score é calculado

Para busca semântica pura:

```sql
-- PgVectorStoreAdapter.java
SELECT 1.0 - (c.embedding <=> ?::vector) AS score
-- O operador <=> calcula distância do cosseno (0 = igual, 2 = oposto)
-- 1.0 - distância = similaridade (1 = igual, 0 = oposto)
```

Para busca híbrida:

```sql
(0.85 * (1.0 - cosine_dist)) + (0.15 * ts_rank(content_tsvector, query)) AS score
```

### Efeitos de aumentar o score mínimo

| Aspecto | Aumentar (ex: 0.10 → 0.40) | Diminuir (ex: 0.10 → 0.01) |
|---------|-----------------------------|------------------------------|
| Precisão | Alta (só chunks muito relevantes) | Baixa (aceita chunks distantes) |
| Recall | Baixa (pode não encontrar nada) | Alta (sempre encontra algo) |
| Risco | LLM diz "não encontrei informação" | LLM alucina com contexto ruim |

### Exemplo prático

O usuário pergunta: *"Qual é o prazo para solicitação de férias?"*

```
Chunks encontrados:
[0.82] "O colaborador deve solicitar férias com 30 dias de antecedência..."
[0.61] "O RH é responsável por aprovar as solicitações de férias..."
[0.12] "A empresa oferece plano de saúde e vale-alimentação..."  ← minScore=0.10: PASSA!
[0.08] "Reunião do comitê foi em março..."                      ← minScore=0.10: DESCARTADO

Se minScore = 0.40:
[0.82] "O colaborador deve solicitar..."  ← ACEITO
[0.61] "O RH é responsável..."            ← ACEITO
[0.12] "A empresa oferece plano..."       ← DESCARTADO
```

Com `minScore=0.40`, o sistema enviaria apenas 2 chunks ao LLM, ambos muito relevantes. Com `minScore=0.10`, enviaria o chunk do plano de saúde também, que não tem nenhuma relação com a pergunta.

### Quando aumentar?

- O LLM está dando respostas imprecisas ou misturando informações
- Os documentos são bem separados por tema (perguntas sobre férias nunca devem trazer RH geral)

### Quando diminuir?

- O sistema frequentemente diz "não encontrei informação" mesmo o documento tendo a resposta
- Documentos com linguagem muito diferente das perguntas (vocabulário técnico vs. coloquial)

---

## 6. Parâmetro: Comprimento mínimo do chunk

**Campo na tela:** slider de 10 a 500 | **Padrão:** 80 caracteres

### O que é

Chunks muito curtos (ex: só um título, um número isolado, um cabeçalho) tendem a ter scores altos na busca vetorial porque seus embeddings são "simples" e capturam pouca informação. Para penalizá-los, o sistema aplica uma **redução de 15% no score** de qualquer chunk menor que este comprimento mínimo.

```java
// ScoredChunk.java
public double adjustedScore(int minLength) {
    if (chunk.content().length() < minLength) {
        return score * 0.85;   // penalidade de 15%
    }
    return score;
}
```

### Exemplo

```
Chunk A: "Artigo 5" (8 caracteres)           → score original: 0.71 → ajustado: 0.60
Chunk B: "O colaborador tem direito a 30 dias de férias anuais, 
          podendo parcelá-las em até 3 períodos conforme..." 
         (120 caracteres)                     → score original: 0.68 → ajustado: 0.68 (sem mudança)
```

Após o ajuste, Chunk B supera Chunk A mesmo tendo score bruto menor.

### Efeitos de aumentar o comprimento mínimo

| Aspecto | Aumentar (ex: 80 → 200) | Diminuir (ex: 80 → 20) |
|---------|--------------------------|------------------------|
| Impacto | Penaliza chunks médios também | Só penaliza chunks muito curtos |
| Risco de aumentar | Chunks legítimos e ricos penalizados | Títulos e cabeçalhos sobem no ranking |

### Quando ajustar?

- **Aumentar:** se seus documentos têm muitos parágrafos curtos (leis, contratos em artigos) e esses parágrafos estão poluindo os resultados.
- **Diminuir:** se seus documentos têm muitos itens de lista curtos que são exatamente o que o usuário quer encontrar (ex: glossário de termos).

---

## 7. Parâmetro: Busca híbrida (semântica + BM25)

**Campo na tela:** toggle (ligado/desligado) | **Padrão:** ligado

### O que é

O sistema oferece dois tipos de busca que podem ser combinados:

#### Busca semântica (vetorial)
Usa embeddings para encontrar textos com **mesmo significado**, mesmo que as palavras sejam diferentes.

```
Pergunta: "Qual o prazo para tirar férias?"
Encontra:  "O colaborador pode solicitar o período de descanso anual com antecedência..."
            ↑ mesma ideia, palavras diferentes — busca semântica encontra!
```

#### Busca BM25 (keyword)
Usa correspondência de palavras-chave com pesos (TF-IDF), como uma busca do Google primitiva.

```
Pergunta: "Qual o prazo para tirar férias?"
Encontra:  "...solicitação de férias deve ser feita com prazo mínimo..."
            ↑ mesmas palavras — BM25 encontra!
```

#### Por que combinar os dois?

Cada método tem pontos cegos:

| Situação | Semântica | BM25 |
|----------|-----------|------|
| Sigla técnica: "FGTS" | Fraca (sigla sem contexto semântico) | Forte (encontra a palavra exata) |
| Sinônimos: "rescisão" vs "demissão" | Forte | Fraca |
| Nome próprio: "João da Silva" | Fraca | Forte |
| Conceito abstrato: "fair use" | Forte | Fraca |

A busca híbrida une as forças de ambos.

### Como o SQL combina os dois

```sql
-- PgVectorStoreAdapter.java — findSimilarHybrid()
SELECT content, metadata,
    (0.85 * (1.0 - (c.embedding <=> ?::vector)))   -- 85% busca vetorial
  + (0.15 * ts_rank(c.content_tsvector, ?))         -- 15% BM25
  AS score
FROM chunks c
WHERE c.namespace = ? AND c.status = 'READY'
ORDER BY score DESC
LIMIT ?
```

### Quando desligar a busca híbrida?

Raramente. Mas pode fazer sentido desligar se:

- Seus documentos são muito técnicos com terminologia exclusiva (siglas, códigos) e a busca semântica está confundindo termos similares de outros domínios
- O banco de dados está com alta carga e você quer simplicidade de query

---

## 8. Parâmetro: Peso semântico

**Campo na tela:** slider de 0,00 a 1,00 | **Padrão:** 0,85
(Só aparece quando a busca híbrida está **ligada**)

### O que é

O peso semântico define a proporção entre busca vetorial e BM25 na pontuação híbrida:

```
Score = (peso_semântico × score_vetorial) + ((1 - peso_semântico) × score_bm25)
Score = (0.85 × score_vetorial) + (0.15 × score_bm25)
```

O complemento `(1 - peso_semântico)` é automaticamente o peso do BM25. Se o peso semântico é 0,85, o peso BM25 é 0,15.

### Visualizando os extremos

```
Peso semântico = 1.0 → 100% vetorial + 0% BM25 (busca puramente semântica)
Peso semântico = 0.0 → 0% vetorial + 100% BM25 (busca puramente por palavras)
Peso semântico = 0.5 → 50% + 50% (balanceado)
Peso semântico = 0.85→ 85% + 15% (padrão: prioriza semântica, BM25 desempata)
```

### Quando mudar?

**Aumentar para 0,90–0,95:** seus usuários fazem perguntas com linguagem natural variada e raramente usam as palavras exatas dos documentos.

```
Usuário pergunta: "Quanto tempo tenho para pedir licença?"
Documento diz:    "O prazo para solicitação de afastamento é de..."
→ Semântica encontra (licença ≈ afastamento), BM25 não (palavras diferentes)
→ Aumente o peso semântico
```

**Diminuir para 0,70–0,75:** seus usuários frequentemente pesquisam por termos exatos (números de artigo, siglas, nomes de produtos).

```
Usuário pergunta: "Art. 35 do regulamento interno"
Documento diz:    "Art. 35 — A jornada de trabalho é de..."
→ BM25 encontra (texto exato), semântica pode errar (Art. 35 não tem semântica clara)
→ Diminua o peso semântico
```

---

## 9. Parâmetro: Limite mensal de tokens

**Campo na tela:** input numérico (mínimo 1.000) | **Padrão:** 100.000

### O que é

É o número máximo de tokens estimados que **cada usuário** pode consumir em um mês. Quando o usuário atinge o limite, o sistema retorna HTTP 429 e a mensagem de erro aparece no chat.

```
Fluxo ao receber uma pergunta:
        ↓
TokenUsageService.checkLimit(username)
        ↓
   tokens_usados_no_mês >= limite?
        ↓ SIM                    ↓ NÃO
TokenLimitExceededException    Pipeline RAG continua normalmente
(HTTP 429)
```

### Por usuário ou global?

**Por usuário.** Cada pessoa tem seu próprio contador. Um usuário que faz 500 perguntas não afeta os outros.

```java
// TokenUsageService.java
public void checkLimit(String username) {
    long limit = ragConfigService.getConfig().monthlyTokenLimit();
    long used  = port.getMonthlyUsage(username, currentYearMonth());
    //                                ^^^^^^^^ — chave é username + mês
    if (used >= limit) {
        throw new TokenLimitExceededException(username, used, limit);
    }
}
```

### Como estimar o limite certo?

Use esta fórmula aproximada:

```
Tokens por pergunta ≈ 2.500 (média com 3 chunks + histórico + system prompt)
Perguntas por usuário por mês ≈ ?

Se usuário faz 20 perguntas/mês:
  20 × 2.500 = 50.000 tokens/mês → coloque 60.000 (com folga de 20%)

Se usuário faz 100 perguntas/mês:
  100 × 2.500 = 250.000 tokens/mês → coloque 300.000
```

### Onde ver o consumo atual?

No frontend, no canto superior direito da topbar há um chip mostrando `XX,XXX tok`:

```javascript
// index.html — loadTokenUsage()
async function loadTokenUsage() {
    const data = await apiFetch('/api/token-usage/me');
    // data.used e data.limit
    chip.textContent = `${fmt(data.used)} / ${fmt(data.limit)} tok`;
}
```

No banco, você pode consultar:

```sql
SELECT username, year_month, tokens_used
FROM token_usage
WHERE year_month = 202605   -- maio de 2026
ORDER BY tokens_used DESC;
```

### Quando aumentar?

- Usuários legítimos estão chegando ao limite antes do fim do mês
- O sistema é de uso interno e não há preocupação de custo por usuário

### Quando diminuir?

- Há usuários fazendo uso abusivo (muitas perguntas automatizadas)
- O custo da API está alto e precisa distribuir o orçamento entre usuários

---

## 10. Parâmetro: Histórico de Chat (mensagens)

**Campo na tela:** slider de 1 a 50 | **Padrão:** 6

### O que é

Quando o usuário faz perguntas em sequência, o sistema envia ao LLM um trecho do **histórico da conversa** para dar contexto. Este parâmetro define **quantas mensagens anteriores** são incluídas.

```
Conversa do usuário (10 mensagens trocadas):
  [M1] Usuário: "Quantos dias de férias tenho?"
  [M2] Bot: "Você tem direito a 30 dias..."
  [M3] Usuário: "E se eu só tirar 15 dias agora?"
  [M4] Bot: "Pode parcelar em até 3 períodos..."
  [M5] Usuário: "Tem alguma restrição no mês de dezembro?"
  [M6] Bot: "Sim, períodos de pico..."
  [M7] Usuário: "Como solicito?"
  [M8] Bot: "Pelo sistema de RH..."
  [M9] Usuário: "Preciso de aprovação?"
  [M10] Bot: "Sim, do gestor direto..."
  [M11] Usuário: "E se meu gestor estiver de férias?"  ← pergunta atual

Com maxHistoryMessages = 6:
  Envia ao LLM: [M5, M6, M7, M8, M9, M10] + pergunta atual [M11]
  (6 mensagens mais recentes antes da atual)

Com maxHistoryMessages = 2:
  Envia ao LLM: [M9, M10] + pergunta atual [M11]
  (o LLM não sabe do contexto sobre parcelamento e restrições de dezembro)
```

### Por que limitar?

Sem limite, uma conversa longa enviaria dezenas de mensagens ao LLM a cada nova pergunta — dobrando ou triplicando o consumo de tokens e aumentando a latência (o LLM leva mais tempo para processar mais texto).

```java
// QueryChatService.java — buildConversation()
private List<ChatMessage> buildConversation(String question, List<ChatMessage> history) {
    int max = config.maxHistoryMessages();
    List<ChatMessage> trimmed = history.size() > max
        ? history.subList(history.size() - max, history.size())  // últimas N mensagens
        : history;
    // ...adiciona pergunta atual ao final
}
```

### Efeitos de aumentar o histórico

| Aspecto | Aumentar (ex: 6 → 20) | Diminuir (ex: 6 → 2) |
|---------|-----------------------|----------------------|
| Coerência de conversa longa | Alta | Baixa (LLM "esquece" o início) |
| Tokens consumidos | Aumenta significativamente | Diminui |
| Latência da resposta | Levemente maior | Menor |
| Custo de API | Sobe | Desce |

### Quando aumentar?

- Usuários fazem conversas longas e contínuas sobre o mesmo tema
- O LLM está perdendo o fio da conversa após poucas mensagens
- O caso de uso é um assistente tipo suporte técnico que investiga um problema em profundidade

### Quando diminuir?

- A maioria das perguntas é independente (usuário não está em uma conversa contínua)
- Custo de API é alto e cada token conta
- O domínio muda completamente entre perguntas (o histórico pode até atrapalhar)

---

## 11. Tabela rápida de referência

| Parâmetro | Padrão | Mín | Máx | Aumentar quando | Diminuir quando |
|-----------|--------|-----|-----|-----------------|-----------------|
| **Top-K** | 3 | 1 | 20 | Respostas incompletas / documentos extensos | LLM confuso / custo alto |
| **Score mínimo** | 0,10 | 0,00 | 1,00 | LLM mistura informações irrelevantes | LLM diz "não encontrei" mesmo tendo a info |
| **Compr. mínimo chunk** | 80 | 10 | 500 | Títulos e cabeçalhos poluindo resultados | Itens curtos de lista são a resposta correta |
| **Busca híbrida** | ligada | — | — | Ligar se há mistura de siglas + linguagem natural | Desligar raramente |
| **Peso semântico** | 0,85 | 0,00 | 1,00 | Usuários usam linguagem variada / sinônimos | Usuários pesquisam termos exatos |
| **Limite tokens/mês** | 100.000 | 1.000 | sem limite | Usuários legítimos atingem o limite | Custo alto / uso abusivo |
| **Histórico chat** | 6 | 1 | 50 | Conversas longas e contínuas | Perguntas independentes / custo alto |

---

## 12. Cenários práticos: quando mudar cada parâmetro

### Cenário A: "O bot não encontra informação que claramente está no documento"

**Diagnóstico:** Score mínimo alto demais ou Top-K muito baixo.

**Ação:**
1. Diminua o score mínimo de 0,10 para 0,05
2. Aumente o Top-K de 3 para 5
3. Teste com a pergunta problemática

### Cenário B: "O bot mistura informações de departamentos diferentes"

**Diagnóstico:** Score mínimo baixo demais — chunks irrelevantes estão passando.

**Ação:**
1. Aumente o score mínimo de 0,10 para 0,30
2. Verifique se cada departamento tem seu próprio **namespace** (isolamento por namespace é a solução estrutural)

### Cenário C: "O bot não lembra o que foi dito há 5 mensagens na mesma conversa"

**Diagnóstico:** Histórico de chat muito baixo.

**Ação:**
1. Aumente o histórico de 6 para 12 ou 20
2. Monitore o consumo de tokens para não explodir o custo

### Cenário D: "Os usuários estão gastando tokens rápido demais"

**Diagnóstico:** Limite mensal de tokens muito alto ou Top-K e histórico muito altos.

**Ação:**
1. Reduza o Top-K (menos chunks = menos tokens por pergunta)
2. Reduza o histórico de chat (menos contexto = menos tokens)
3. Se necessário, diminua o limite mensal para forçar uso mais moderado

### Cenário E: "Termos técnicos específicos (siglas, códigos de produto) não são encontrados"

**Diagnóstico:** Busca semântica fraca para termos sem semântica (siglas).

**Ação:**
1. Certifique-se de que a busca híbrida está **ligada**
2. Diminua o peso semântico de 0,85 para 0,70 (dá mais peso ao BM25/keyword)
3. Ou deixe em 0,85 e ajuste o Score mínimo para aceitar BM25 hits mesmo com score moderado

### Cenário F: "As respostas estão lentas"

**Diagnóstico:** Top-K alto + histórico alto = muito texto enviado ao LLM.

**Ação:**
1. Reduza o Top-K de 5 para 3
2. Reduza o histórico de 12 para 6
3. A latência vai cair proporcionalmente ao volume de texto enviado

---

## 13. Como as mudanças chegam ao backend (sem redeploy)

Uma das características importantes desta tela é que as mudanças têm **efeito imediato sem redeploy**. Isso é possível porque:

1. **Os valores ficam no banco** (tabela `rag_config`), não no `application.yml`
2. **O `RagConfigService` tem cache de 30s**: qualquer pergunta feita após 30 segundos da mudança já usará os novos valores (ou imediatamente, se o admin acabou de salvar)
3. **O `application.yml` define apenas os valores iniciais** — a tela os sobrescreve no banco

```
Antes da tela Config IA existir:
  application.yml → valores fixos → redeploy obrigatório para mudar

Depois da tela Config IA:
  application.yml → valores iniciais (padrão)
  banco de dados  → valores ativos (atualizados pela tela)
  RagConfigService→ lê banco com cache 30s
                       ↓
              Mudança na tela → banco atualizado
                       ↓
              Próxima pergunta → usa novo valor ✓
```

---

## 14. Quem pode alterar a configuração?

Apenas usuários com **role ADMIN** podem ver e salvar a tela de configuração.

```java
// RagConfigController.java
@RestController
@RequestMapping("/api/admin/config")
@PreAuthorize("hasRole('ADMIN')")   // ← bloqueia VIEWER e EDITOR
public class RagConfigController { ... }
```

No frontend, o item de menu "Config IA" só aparece para ADMIN:

```javascript
// index.html
if (payload.role === 'ADMIN') {
    document.getElementById('nav-config-ia').style.display = 'flex';
}
```

O log de auditoria registra cada alteração:

```sql
-- Toda mudança na configuração aparece no audit_log:
SELECT username, action, target_type, detail, created_at
FROM audit_log
WHERE action = 'CONFIG_UPDATE'
ORDER BY created_at DESC;

-- username | action        | detail
-- admin    | CONFIG_UPDATE | topK=5 minScore=0.10 hybrid=true
```

Isso garante rastreabilidade: se alguém mudar a configuração e o comportamento do bot piorar, é possível ver exatamente quem mudou, quando e para quais valores.

---

*Documentação gerada em maio de 2026 — projeto RagBot Fase 4*
