# Deploy RagBot — Railway + Supabase

Guia de referência para replicar o deploy do zero.

---

## O que foi configurado (contexto)

| Arquivo | O que faz |
|---|---|
| `Dockerfile` | Multi-stage build Alpine, JVM otimizada para 512 MB |
| `src/main/resources/application-prod.yml` | Perfil de produção: HikariCP=2, logs WARN, async threads=2 |
| `scripts/deploy-railway.ps1` | Script que faz tudo automaticamente |

---

## Pré-requisitos (uma vez só)

### 1. Railway CLI
```powershell
# Já instalado na máquina. Para reinstalar:
npm install -g @railway/cli
railway --version   # deve mostrar 4.x
```

### 2. Conta Railway
1. Acesse **https://railway.app**
2. Clique em **"Start a New Project"** → **"Sign in with GitHub"**
3. Autorize o acesso ao GitHub
4. O plano **Hobby** inclui **$5/mês de crédito** — suficiente para 30 dias

### 3. Conta Supabase (banco de dados gratuito com pgvector)
1. Acesse **https://supabase.com**
2. Clique em **"Start your project"** → **"Sign in with GitHub"**
3. Crie um novo projeto:
   - **Organization:** sua org pessoal
   - **Project name:** `ragbot-db`
   - **Database Password:** gere uma senha forte e **salve em local seguro**
   - **Region:** escolha o mais próximo (ex: `South America (São Paulo)`)
4. Aguarde ~2 min para o projeto provisionar

---

## Configuração do Supabase

### Ativar extensão pgvector (obrigatório)
1. No dashboard do projeto → **Database** → **Extensions**
2. Busque `vector`
3. Clique em **Enable**

> O RagBot usa embeddings de 3072 dimensões (Gemini). Sem pgvector o app não sobe.

### Obter a URL de conexão
1. **Settings** → **Database** → **Connection string**
2. Selecione a aba **"Transaction pooler"** (porta **6543**)
3. Copie a URL — formato:
   ```
   postgresql://postgres.[project-ref]:[senha]@aws-0-[regiao].pooler.supabase.com:6543/postgres
   ```
4. Guarde essa URL — você vai precisar no script de deploy

---

## Executar o deploy

```powershell
# No PowerShell, dentro da pasta do projeto:
cd C:\ragbot_fase4\ragbot_fase4

# Executar o script (bypass de execution policy apenas para esta sessão)
Set-ExecutionPolicy Bypass -Scope Process
.\scripts\deploy-railway.ps1
```

O script vai pedir interativamente:
1. **Login Railway** → abre o browser, você autoriza
2. **URL do Supabase** → cole a URL copiada acima
3. **GROQ_API_KEY** → sua key do Groq (gratuito em console.groq.com/keys)
4. **GOOGLE_API_KEY** → sua key do Google AI Studio (opcional)
5. **Nome do projeto Railway** → ex: `ragbot-producao`

Duração total: ~5 min (mais 3–5 min de build no Railway).

---

## Comandos Railway úteis do dia a dia

```powershell
$R = "C:\Users\AbnerDev\AppData\Roaming\npm\node_modules\@railway\cli\bin\railway.exe"

# Ver logs em tempo real
& $R logs --follow

# Ver variáveis de ambiente configuradas
& $R variables

# Alterar uma variável
& $R variables set GROQ_MODEL=llama-3.1-8b-instant

# Forçar novo deploy (sem alterar código)
& $R redeploy

# Ver status e URL da aplicação
& $R status

# Pausar o serviço (para economizar crédito)
& $R down

# Reativar o serviço
& $R up
```

---

## Verificar se o deploy funcionou

```bash
# Substitua pela URL que o Railway forneceu
curl https://seu-projeto.up.railway.app/actuator/health
# Esperado: {"status":"UP"}
```

---

## Estimativa de custo Railway ($5 de crédito / 30 dias)

| Serviço | RAM usada | Custo estimado/mês |
|---|---|---|
| Railway — Spring Boot container | ~307 MB (60% de 512 MB) | ~$2,80 |
| Supabase PostgreSQL | — (free tier) | **$0,00** |
| **Total** | | **~$2,80** |
| **Sobra de crédito** | | **~$2,20** |

> Para economizar ainda mais: use `railway down` quando não estiver apresentando.
> O serviço sobe em ~60 segundos com `railway up`.

---

## Variáveis de ambiente configuradas automaticamente

| Variável | Valor | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | Ativa `application-prod.yml` |
| `SPRING_DATASOURCE_URL` | URL Supabase | Conexão JDBC com sslmode=require |
| `SPRING_DATASOURCE_USERNAME` | extraído da URL | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | extraído da URL | Senha do banco |
| `JWT_SECRET` | gerado automaticamente | 64 chars hex aleatório |
| `GROQ_API_KEY` | sua chave | Provider de LLM principal |
| `GOOGLE_API_KEY` | sua chave | Provider de embedding |
| `ASYNC_CORE_POOL` | `1` | Threads de ingestão (reduzido de 2) |
| `ASYNC_MAX_POOL` | `2` | Máximo de threads (reduzido de 5) |
| `JAVA_OPTS` | `` | Vazio — flags já estão no Dockerfile |

---

## Troubleshooting comum

### App não sobe (OOMKilled)
```powershell
& $R variables set JAVA_OPTS="-XX:MaxRAMPercentage=65.0"
& $R redeploy
```

### Flyway falha na migration
```powershell
# Pool de 2 conexões já resolve na maioria dos casos. Se persistir:
& $R variables set SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT=30000
```

### pgvector não encontrado
```
# Verifique no Supabase:
# Dashboard → Database → Extensions → "vector" deve estar ON
```

### Erro de SSL com Supabase
```powershell
# Se sslmode=require falhar, tente:
& $R variables set SPRING_DATASOURCE_URL="jdbc:postgresql://...?sslmode=no-verify"
```
