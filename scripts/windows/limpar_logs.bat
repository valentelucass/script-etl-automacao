@echo off
setlocal EnableExtensions
REM ==[DOC-FILE]===============================================================
REM Arquivo : limpar_logs.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "limpar logs" para uso operacional.
REM
REM Conecta com:
REM - Conexoes diretas nao detectadas por analise estatica.
REM
REM Fluxo geral:
REM 1) Inicializa variaveis de controle do script.
REM 2) Executa rotina principal com validacoes basicas.
REM 3) Finaliza com codigo de retorno apropriado.
REM
REM Variaveis-chave:
REM - BASE_DIR: controle de estado do script.
REM - LOGS_DIR: controle de estado do script.
REM - FULL_CLEAN: controle de estado do script.
REM [DOC-FILE-END]===========================================================

set "BASE_DIR=%~dp0"
set "LOGS_DIR=%BASE_DIR%logs"
set "FULL_CLEAN=0"

if /i "%~1"=="/full" set "FULL_CLEAN=1"

echo Limpando arquivos de log em: "%LOGS_DIR%"
echo Observacao: arquivos .csv serao preservados.

if not exist "%LOGS_DIR%" (
    mkdir "%LOGS_DIR%" 2>nul
)

for /r "%LOGS_DIR%" %%F in (*.log) do (
    del /f /q "%%~fF" 2>nul
)

if "%FULL_CLEAN%"=="1" (
    echo.
    echo [Modo FULL] Limpando artefatos temporarios adicionais...

    if exist "%BASE_DIR%target\producao_bat_test" (
        rmdir /s /q "%BASE_DIR%target\producao_bat_test" 2>nul
    )

    del /q "%BASE_DIR%target\tmp_*.*" 2>nul
    del /q "%BASE_DIR%target\loop_after_fix.log" 2>nul

    if exist "%BASE_DIR%src\dashboards\__pycache__" (
        rmdir /s /q "%BASE_DIR%src\dashboards\__pycache__" 2>nul
    )
    if exist "%BASE_DIR%scripts\__pycache__" (
        rmdir /s /q "%BASE_DIR%scripts\__pycache__" 2>nul
    )

    del /q "%LOGS_DIR%\manual_api_db_compare_*.md" 2>nul
    del /q "%LOGS_DIR%\manual_api_db_compare_*.json" 2>nul
    del /q "%LOGS_DIR%\_analysis_report.md" 2>nul
    del /q "%LOGS_DIR%\_analysis_report.json" 2>nul
    del /q "%LOGS_DIR%\__in_export.txt" 2>nul
    del /q "%LOGS_DIR%\daemon\loop_daemon.pid" 2>nul
    del /q "%LOGS_DIR%\daemon\runtime\extrator-daemon-runtime.jar" 2>nul

    del /q "%BASE_DIR%last_run.properties" 2>nul
)

mkdir "%LOGS_DIR%\daemon\ciclos" 2>nul
mkdir "%LOGS_DIR%\daemon\history" 2>nul
mkdir "%LOGS_DIR%\daemon\reconciliacao" 2>nul
mkdir "%LOGS_DIR%\daemon\runtime" 2>nul

echo Limpeza concluida.
if "%FULL_CLEAN%"=="0" (
    echo Dica: use "limpar_logs.bat /full" para remover temporarios de teste e cache local.
)
exit /b 0
