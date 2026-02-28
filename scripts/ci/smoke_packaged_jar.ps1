$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Path $PSScriptRoot -Parent | Split-Path -Parent
Set-Location $repoRoot

$jarPath = Join-Path $repoRoot "target\extrator.jar"
if (-not (Test-Path $jarPath)) {
    throw "ERRO: JAR nao encontrado em $jarPath"
}

$smokeDir = Join-Path $repoRoot "target\ci\security-smoke"
if (Test-Path $smokeDir) {
    Remove-Item -Recurse -Force $smokeDir
}
New-Item -ItemType Directory -Path $smokeDir | Out-Null

Write-Host "[smoke] Validando comando de ajuda no JAR empacotado..."
& java --enable-native-access=ALL-UNNAMED -jar $jarPath --ajuda | Out-Null

Write-Host "[smoke] Validando inicializacao do modulo de seguranca (SQLite) no JAR empacotado..."
& java "-Dextrator.security.db.path=$smokeDir\users.db" --enable-native-access=ALL-UNNAMED -jar $jarPath --auth-info | Out-Null

$dbFile = Join-Path $smokeDir "users.db"
if (-not (Test-Path $dbFile)) {
    throw "ERRO: Banco SQLite de seguranca nao foi criado pelo smoke test."
}

Write-Host "[smoke] OK: JAR empacotado validado com sucesso."
