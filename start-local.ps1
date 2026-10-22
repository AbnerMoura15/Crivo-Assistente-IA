[CmdletBinding()]
param(
    [int]$PreferredPort = 8081,
    [switch]$PrepareOnly
)

$ErrorActionPreference = "Stop"

function Test-PortInUse {
    param([int]$Port)

    $connection = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    return $null -ne $connection
}

function Get-FreePort {
    param([int]$StartPort)

    for ($port = $StartPort; $port -lt ($StartPort + 20); $port++) {
        if (-not (Test-PortInUse -Port $port)) {
            return $port
        }
    }

    throw "Nenhuma porta livre encontrada entre $StartPort e $($StartPort + 19)."
}

function Wait-PostgresHealthy {
    param([int]$TimeoutSeconds = 90)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $state = docker inspect ragbot-postgres --format "{{.State.Status}}|{{if .State.Health}}{{.State.Health.Status}}{{end}}" 2>$null
        if ($LASTEXITCODE -eq 0 -and $state) {
            $parts = $state -split "\|", 2
            $status = $parts[0]
            $health = if ($parts.Count -gt 1) { $parts[1] } else { "" }
            if ($status -eq "running" -and ($health -eq "" -or $health -eq "healthy")) {
                return
            }
        }

        Start-Sleep -Seconds 2
    }

    throw "O container ragbot-postgres nao ficou saudavel a tempo."
}

Write-Host "Subindo PostgreSQL do projeto..."
docker compose up -d | Out-Host
Wait-PostgresHealthy

$port = Get-FreePort -StartPort $PreferredPort
Write-Host "Banco pronto. Porta escolhida para a API: $port"

if ($PrepareOnly) {
    Write-Host "Ambiente preparado. Rode:"
    Write-Host "mvn spring-boot:run ""-Dspring-boot.run.arguments=--server.port=$port"""
    exit 0
}

Write-Host "Iniciando aplicacao em http://localhost:$port"
& "C:\Program Files\apache-maven-3.9.15\bin\mvn.cmd" "spring-boot:run" "-Dspring-boot.run.arguments=--server.port=$port"
