$ErrorActionPreference = "Stop"

param(
    [switch]$ComFaturasGraphql
)

$repoRoot = Split-Path -Path $PSScriptRoot -Parent | Split-Path -Parent
Set-Location $repoRoot

$jarPath = Join-Path $repoRoot "target\extrator.jar"
if (-not (Test-Path $jarPath)) {
    throw "ERRO: JAR nao encontrado em $jarPath"
}

$flagFaturas = if ($ComFaturasGraphql) { "" } else { "--sem-faturas-graphql" }
$modoFaturas = if ($ComFaturasGraphql) { "com faturas GraphQL" } else { "sem faturas GraphQL" }

function Invoke-Step {
    param(
        [string]$Titulo,
        [string]$Args
    )

    Write-Host ""
    Write-Host "[$(Get-Date -Format 'HH:mm:ss')] $Titulo"
    Write-Host "Comando: java --enable-native-access=ALL-UNNAMED -jar target\\extrator.jar $Args"
    & java --enable-native-access=ALL-UNNAMED -jar $jarPath $Args.Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)
    if ($LASTEXITCODE -ne 0) {
        throw "Falha na etapa: $Titulo (exit code=$LASTEXITCODE)"
    }
}

Write-Host "Rodada 24h consistente iniciada ($modoFaturas)."
Invoke-Step -Titulo "Etapa 1/3 - Extracao fresca" -Args "--fluxo-completo $flagFaturas"
Invoke-Step -Titulo "Etapa 2/3 - Validacao detalhada" -Args "--validar-api-banco-24h-detalhado $flagFaturas"
Invoke-Step -Titulo "Etapa 3/3 - Validacao resumida" -Args "--validar-api-banco-24h $flagFaturas"
Write-Host ""
Write-Host "Rodada 24h consistente finalizada com sucesso."
