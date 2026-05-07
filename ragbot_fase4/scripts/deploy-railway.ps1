#Requires -Version 5.1
<#
.SYNOPSIS
    Deploy completo do RagBot no Railway + Supabase.
    Execute este script UMA VEZ para configurar tudo do zero.

.HOW TO RUN
    1. Abra o PowerShell como usuario normal (nao precisa de Admin)
    2. Navegue ate a pasta do projeto:
         cd C:\ragbot_fase4\ragbot_fase4
    3. Execute:
         Set-ExecutionPolicy Bypass -Scope Process; .\scripts\deploy-railway.ps1

.PRE-REQUISITOS
    - Node.js instalado (ja esta na sua maquina)
    - Railway CLI instalado (ja instalado: C:\Users\AbnerDev\AppData\Roaming\npm\railway.ps1)
    - Conta no Railway (railway.app) - pode criar gratis com GitHub
    - Conta no Supabase (supabase.com) - free tier com pgvector
    - Repositorio do projeto commitado no GitHub
#>

Set-StrictMode -Off
$ErrorActionPreference = "Stop"
$RAILWAY = "C:\Users\AbnerDev\AppData\Roaming\npm\node_modules\@railway\cli\bin\railway.exe"

# ── Cores para output ────────────────────────────────────────────────────────
function Write-Step  { param($msg) Write-Host "`n[PASSO] $msg" -ForegroundColor Cyan }
function Write-Ok    { param($msg) Write-Host "  [OK] $msg"  -ForegroundColor Green }
function Write-Info  { param($msg) Write-Host "  [>>] $msg"  -ForegroundColor Yellow }
function Write-Err   { param($msg) Write-Host "  [!!] $msg"  -ForegroundColor Red }
function Pause-Step  { param($msg) Write-Host "`n  >>> $msg`n  Pressione ENTER para continuar..." -ForegroundColor Magenta; Read-Host | Out-Null }

# ── Banner ───────────────────────────────────────────────────────────────────
Clear-Host
Write-Host @"
╔══════════════════════════════════════════════════════════╗
║          RAGBOT — DEPLOY RAILWAY + SUPABASE              ║
║          Script gerado por Claude Code                   ║
╚══════════════════════════════════════════════════════════╝
"@ -ForegroundColor Cyan

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 0 — Verificacoes iniciais
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Verificando pre-requisitos..."

if (-not (Test-Path $RAILWAY)) {
    Write-Err "Railway CLI nao encontrado em $RAILWAY"
    Write-Info "Instale com: npm install -g @railway/cli"
    exit 1
}
Write-Ok "Railway CLI encontrado"

$railwayVersion = & $RAILWAY --version 2>&1
Write-Ok "Railway CLI versao: $railwayVersion"

if (-not (Test-Path ".\pom.xml")) {
    Write-Err "Execute este script a partir da pasta raiz do projeto (onde esta o pom.xml)"
    exit 1
}
Write-Ok "Projeto encontrado (pom.xml presente)"

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 1 — Login no Railway
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Login no Railway..."
Write-Info "Uma aba do browser vai abrir. Autorize o acesso e volte aqui."
Write-Info "Se ja estiver logado, o comando termina imediatamente."
& $RAILWAY login
Write-Ok "Autenticado no Railway"

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 2 — Configuracao do Supabase
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Configuracao do Supabase (banco de dados)"
Write-Host @"

  Como obter a URL de conexao do Supabase:
  ─────────────────────────────────────────
  1. Acesse: https://supabase.com/dashboard
  2. Selecione seu projeto (ou crie um novo gratis)
  3. Va em: Settings → Database → Connection string
  4. Selecione "Transaction pooler" (porta 6543) — melhor para serverless
  5. Copie a URL no formato:
       postgresql://postgres.[ref]:[senha]@aws-0-[regiao].pooler.supabase.com:6543/postgres

  IMPORTANTE: Ative a extensao pgvector:
  1. Va em: Database → Extensions
  2. Busque "vector" e ative

"@ -ForegroundColor Yellow

Pause-Step "Abra o Supabase agora, copie a URL e ative o pgvector. Volte aqui quando pronto."

$supabaseUrl = Read-Host "  Cole a URL do Supabase aqui"
$supabaseUrl = $supabaseUrl.Trim()

# Converte postgres:// para jdbc: se necessario
if ($supabaseUrl -match "^postgres://") {
    $supabaseUrl = $supabaseUrl -replace "^postgres://", "postgresql://"
}
if (-not $supabaseUrl.StartsWith("jdbc:")) {
    $jdbcUrl = "jdbc:$supabaseUrl" + "?sslmode=require"
} else {
    $jdbcUrl = $supabaseUrl
}

# Extrai usuario e senha da URL para Spring Boot
# Formato: jdbc:postgresql://usuario:senha@host:porta/db?...
if ($jdbcUrl -match "jdbc:postgresql://([^:]+):([^@]+)@") {
    $dbUser = $Matches[1]
    $dbPass = $Matches[2]
    Write-Ok "URL do banco configurada (usuario: $dbUser)"
} else {
    Write-Err "Formato de URL invalido. Esperado: postgresql://usuario:senha@host:porta/banco"
    exit 1
}

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 3 — Credenciais das APIs de IA
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Credenciais das APIs de IA..."
Write-Info "Pelo menos uma API e obrigatoria (Groq e gratuita e recomendada para demo)."
Write-Info "Groq: https://console.groq.com/keys"
Write-Info "Google: https://aistudio.google.com/apikey"
Write-Host ""

$groqKey   = Read-Host "  GROQ_API_KEY (Enter para pular)"
$googleKey = Read-Host "  GOOGLE_API_KEY (Enter para pular)"

if ([string]::IsNullOrWhiteSpace($groqKey) -and [string]::IsNullOrWhiteSpace($googleKey)) {
    Write-Err "Informe pelo menos uma API key (Groq ou Google)"
    exit 1
}

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 4 — JWT Secret
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Gerando JWT Secret seguro..."
$jwtBytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($jwtBytes)
$jwtSecret = [System.BitConverter]::ToString($jwtBytes).Replace("-", "").ToLower()
Write-Ok "JWT Secret gerado (64 chars hex)"

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 5 — Criar ou vincular projeto Railway
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Configurando projeto no Railway..."

$projectName = Read-Host "  Nome do projeto Railway (ex: ragbot-producao)"
if ([string]::IsNullOrWhiteSpace($projectName)) { $projectName = "ragbot-producao" }

Write-Info "Criando projeto '$projectName'..."
& $RAILWAY init --name $projectName
Write-Ok "Projeto '$projectName' criado/vinculado"

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 6 — Definir variaveis de ambiente no Railway
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Configurando variaveis de ambiente no Railway..."

$vars = @{
    "SPRING_PROFILES_ACTIVE"     = "prod"
    "SPRING_DATASOURCE_URL"      = $jdbcUrl
    "SPRING_DATASOURCE_USERNAME" = $dbUser
    "SPRING_DATASOURCE_PASSWORD" = $dbPass
    "JWT_SECRET"                 = $jwtSecret
    "ASYNC_CORE_POOL"            = "1"
    "ASYNC_MAX_POOL"             = "2"
    "ASYNC_QUEUE"                = "10"
    "JAVA_OPTS"                  = ""
}

if (-not [string]::IsNullOrWhiteSpace($groqKey))  { $vars["GROQ_API_KEY"]   = $groqKey }
if (-not [string]::IsNullOrWhiteSpace($googleKey)) { $vars["GOOGLE_API_KEY"] = $googleKey }

# Monta array "KEY=VAL" para o comando railway variables set
$varArgs = $vars.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }

Write-Info "Enviando $($varArgs.Count) variaveis..."
& $RAILWAY variables set @varArgs
Write-Ok "Variaveis configuradas"

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 7 — Deploy
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Iniciando deploy (Railway vai fazer o build com o Dockerfile)..."
Write-Info "Isso pode levar 3-5 minutos na primeira vez (download das dependencias Maven)."
Write-Info "Rebuilds sao muito mais rapidos por causa do cache de camadas Docker."

& $RAILWAY up --detach
Write-Ok "Deploy enviado para o Railway"

# ════════════════════════════════════════════════════════════════════════════
# ETAPA 8 — Exibir resultado
# ════════════════════════════════════════════════════════════════════════════
Write-Step "Obtendo URL da aplicacao..."
Start-Sleep -Seconds 3
$domain = & $RAILWAY domain 2>&1
Write-Ok "Deploy iniciado!"

Write-Host @"

╔══════════════════════════════════════════════════════════╗
║                   DEPLOY CONCLUIDO!                      ║
╠══════════════════════════════════════════════════════════╣
║                                                          ║
║  Monitorar logs em tempo real:                           ║
║    railway logs --follow                                 ║
║                                                          ║
║  Verificar saude:                                        ║
║    curl https://$domain/actuator/health                  ║
║                                                          ║
║  Parar o servico (economiza credito):                    ║
║    railway down                                          ║
║                                                          ║
╚══════════════════════════════════════════════════════════╝
"@ -ForegroundColor Green

Write-Host "`n  URL: https://$domain" -ForegroundColor White
Write-Host "  Dashboard: https://railway.app/dashboard`n" -ForegroundColor Yellow
