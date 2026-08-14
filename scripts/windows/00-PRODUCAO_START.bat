@echo off
setlocal EnableExtensions EnableDelayedExpansion

if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 65001 >nul

set "SCRIPT_ROOT=%~dp0"
for %%I in ("%~dp0..\..") do set "REPO_ROOT=%%~fI"
pushd "%REPO_ROOT%"
set "JAVA_BASE_OPTS=--enable-native-access=ALL-UNNAMED "-DETL_BASE_DIR=%REPO_ROOT%" "-Detl.base.dir=%REPO_ROOT%""
set "JAR_PATH=%REPO_ROOT%\target\extrator.jar"
set "BASICS_READY=0"
set "STARTUP_READY=0"
set "SQLITE_AUTH_DB="
set "AUTH_SESSION_ACTIVE=0"
set "AUTH_SESSION_USER="
set "AUTH_SESSION_ROLE="
set "AUTH_SESSION_ACTIONS="

REM ----------------------------------------------------------------
REM PRODUCAO: apenas executa artefato ja gerado.
REM Nao compila, nao testa, nao empacota.
REM Para compilar, use o ambiente de desenvolvimento separadamente.
REM ----------------------------------------------------------------
set "PROD_MODE=1"
set "PROD_NONINTERACTIVE=0"
set "BACKFILL_DIRECT=0"
if /i "%~1"=="--hard-backfill" (
    set "PROD_NONINTERACTIVE=1"
    set "BACKFILL_DIRECT=1"
)
if /i "%~1"=="--backfill-completo" (
    set "PROD_NONINTERACTIVE=1"
    set "BACKFILL_DIRECT=1"
)
if /i "%~1"=="--auto-intervalo" set "PROD_NONINTERACTIVE=1"
if /i "%~1"=="--auto-extracao-completa" set "PROD_NONINTERACTIVE=1"

if "%BACKFILL_DIRECT%"=="1" goto :RUN_BACKFILL_COMPLETO
if /i "%~1"=="--auto-intervalo" goto :RUN_AUTO_INTERVALO
if /i "%~1"=="--auto-extracao-completa" (
    call :PREPARE_SECURITY
    if errorlevel 1 (
        set "AUTO_EXIT=1"
        goto :END_WITH_CODE
    )
    call :EXECUTION_SAFETY_GATE "extracao completa automatica"
    if errorlevel 1 (
        set "AUTO_EXIT=1"
        goto :END_WITH_CODE
    )
    call :PREPARE_DATABASE
    if errorlevel 1 (
        set "AUTO_EXIT=1"
        goto :END_WITH_CODE
    )
    call :RUN_SCRIPT_AUTHORIZED "01-executar_extracao_completa.bat" "%~2"
    if errorlevel 1 (
        set "AUTO_EXIT=!ERRORLEVEL!"
        echo.
        echo [AVISO] Materializacao das fatos BI ignorada porque a extracao retornou codigo !AUTO_EXIT!.
    ) else (
        call :MATERIALIZAR_FATOS_BI_POST_RUN
        set "AUTO_EXIT=!ERRORLEVEL!"
    )
    goto :END_WITH_CODE
)

:MENU
cls
echo ================================================================
echo            MENU DE PRODUCAO - EXTRATOR ESL CLOUD
echo            suporte: lucasmac.dev@gmail.com
echo            by: @valentelucass
echo ================================================================
echo.
echo 01. Extracao completa operacional ^(D-1..D + replay/validacao + Raster se habilitada^)
echo 02. Loop daemon de extracao 60 minutos ^(inclui inventario, sinistros e Raster se habilitada^)
echo 03. Backfill Completo ^(90 dias^) - Schema Ja Pronto
echo 04. Extracao por intervalo ^(inclui inventario, sinistros e Raster se habilitada^)
echo 05. Testar API especifica ^(GraphQL, DataExport ou Raster^)
echo 06. Validar configuracoes
echo 07. Bateria extrema e relatorio de saude do ETL
echo 08. Exportar CSV
echo 09. Auditar estrutura das APIs
echo 10. Ver ajuda de comandos
echo 11. Gerenciar usuarios de acesso ^(tecla U^)
echo 12. Extracao rapida ultimas 24h ^(sem replay^)
echo 13. Retrofit global por periodo ^(todas as fontes; exige Daemon parado^)
echo 00. Sair
echo.
echo Cobertura atual do ETL:
echo   GraphQL   = coletas, fretes, usuarios_sistema
echo   DataExport = manifestos, cotacoes, localizacao_cargas, contas_a_pagar, faturas_por_cliente, inventario, sinistros
echo   Raster    = raster_viagens e raster_viagem_paradas ^(quando RASTER_ENABLED/credenciais habilitarem^)
echo.
if not "%BASICS_READY%"=="1" (
    echo Ambiente sera validado ao executar a primeira opcao.
    echo.
) else if not "%STARTUP_READY%"=="1" (
    echo Banco e tabelas base serao validados apos a autenticacao da primeira acao que exigir escrita.
    echo.
)
call :READ_MENU_OPTION
if errorlevel 2 (
    echo.
    echo Opcao invalida.
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
if errorlevel 1 (
    echo.
    echo Entrada encerrada. Encerrando menu de producao.
    goto :END
)

if "%OP%"=="1" goto :RUN_01
if "%OP%"=="2" goto :RUN_05
if "%OP%"=="3" goto :RUN_BACKFILL_COMPLETO
if "%OP%"=="4" goto :RUN_04
if "%OP%"=="5" goto :RUN_02
if "%OP%"=="6" goto :RUN_03
if "%OP%"=="7" goto :RUN_06
if "%OP%"=="8" goto :RUN_07
if "%OP%"=="9" goto :RUN_08
if "%OP%"=="10" goto :RUN_AJUDA
if "%OP%"=="11" goto :RUN_09
if "%OP%"=="12" goto :RUN_11
if "%OP%"=="13" goto :RUN_RETROFIT
if "%OP%"=="0" goto :TRY_EXIT
if "%OP%"=="00" goto :TRY_EXIT

echo.
echo Opcao invalida.
timeout /t 2 /nobreak >nul 2>&1
goto :MENU

:RUN_AUTO_INTERVALO
if "%~2"=="" (
    echo.
    echo ERRO: Data de inicio nao informada para o modo automatico.
    echo Uso: 00-PRODUCAO_START.bat --auto-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--modo-rapido-24h]
    echo Exemplo DataExport: 00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 dataexport inventario
    echo Exemplo Raster:     00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 raster
    set "AUTO_EXIT=1"
    goto :END_WITH_CODE
)
if "%~3"=="" (
    echo.
    echo ERRO: Data de fim nao informada para o modo automatico.
    echo Uso: 00-PRODUCAO_START.bat --auto-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--modo-rapido-24h]
    echo Exemplo DataExport: 00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 dataexport sinistros
    echo Exemplo Raster:     00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 raster
    set "AUTO_EXIT=1"
    goto :END_WITH_CODE
)
call :PREPARE_SECURITY
if errorlevel 1 (
    set "AUTO_EXIT=1"
    goto :END_WITH_CODE
)
call :EXECUTION_SAFETY_GATE "extracao por intervalo automatica"
if errorlevel 1 (
    set "AUTO_EXIT=1"
    goto :END_WITH_CODE
)
call :PREPARE_DATABASE
if errorlevel 1 (
    set "AUTO_EXIT=1"
    goto :END_WITH_CODE
)
if "%~2"=="" (
    echo.
    echo ERRO: Data de inicio nao informada para o modo automatico.
    echo Uso: 00-PRODUCAO_START.bat --auto-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--modo-rapido-24h]
    echo Exemplo DataExport: 00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 dataexport inventario
    echo Exemplo Raster:     00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 raster
    set "AUTO_EXIT=1"
    goto :END_WITH_CODE
)
if "%~3"=="" (
    echo.
    echo ERRO: Data de fim nao informada para o modo automatico.
    echo Uso: 00-PRODUCAO_START.bat --auto-intervalo YYYY-MM-DD YYYY-MM-DD [api] [entidade] [--modo-rapido-24h]
    echo Exemplo DataExport: 00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 dataexport sinistros
    echo Exemplo Raster:     00-PRODUCAO_START.bat --auto-intervalo 2026-04-01 2026-04-02 raster
    set "AUTO_EXIT=1"
    goto :END_WITH_CODE
)
set "PREV_SKIP_AUTH_CHECK=%EXTRATOR_SKIP_AUTH_CHECK%"
set "PREV_NONINTERACTIVE=%EXTRATOR_NONINTERACTIVE%"
set "EXTRATOR_SKIP_AUTH_CHECK=1"
set "EXTRATOR_NONINTERACTIVE=1"
call "%SCRIPT_ROOT%04-extracao_por_intervalo.bat" "%~2" "%~3" "%~4" "%~5" "%~6" "%~7"
if errorlevel 1 (
    set "AUTO_EXIT=!ERRORLEVEL!"
    echo.
    echo [AVISO] Materializacao das fatos BI ignorada porque a extracao retornou codigo !AUTO_EXIT!.
) else (
    call :MATERIALIZAR_FATOS_BI_POST_RUN
    set "AUTO_EXIT=!ERRORLEVEL!"
)
if defined PREV_SKIP_AUTH_CHECK (
    set "EXTRATOR_SKIP_AUTH_CHECK=%PREV_SKIP_AUTH_CHECK%"
) else (
    set "EXTRATOR_SKIP_AUTH_CHECK="
)
if defined PREV_NONINTERACTIVE (
    set "EXTRATOR_NONINTERACTIVE=%PREV_NONINTERACTIVE%"
) else (
    set "EXTRATOR_NONINTERACTIVE="
)
goto :END_WITH_CODE

:RUN_BACKFILL_COMPLETO
set "BACKFILL_EXIT=0"
set "BACKFILL_DATA_INICIO="
set "BACKFILL_DATA_FIM="

call :PREPARE_SECURITY
if errorlevel 1 (
    set "BACKFILL_EXIT=1"
    goto :BACKFILL_COMPLETO_DONE
)

call :EXECUTION_SAFETY_GATE "Backfill completo 90 dias"
if errorlevel 1 (
    set "BACKFILL_EXIT=1"
    goto :BACKFILL_COMPLETO_DONE
)

if not "%BACKFILL_DIRECT%"=="1" (
    echo.
    echo ================================================================
    echo   BACKFILL COMPLETO - SCHEMA JA PRONTO
    echo ================================================================
    echo Nenhuma DDL sera executada por este fluxo.
    echo O banco e as tabelas base devem existir antes da carga.
    echo Pressione qualquer tecla para validar o schema e iniciar o backfill.
    echo ================================================================
    pause >nul
)

call :PREPARE_DATABASE
if errorlevel 1 (
    set "BACKFILL_EXIT=1"
    goto :BACKFILL_COMPLETO_DONE
)

for /f "delims=" %%D in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Date).AddDays(-90).ToString('yyyy-MM-dd')"') do set "BACKFILL_DATA_INICIO=%%D"
for /f "delims=" %%D in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Date).ToString('yyyy-MM-dd')"') do set "BACKFILL_DATA_FIM=%%D"

if not defined BACKFILL_DATA_INICIO (
    echo.
    echo [ERRO] Nao foi possivel calcular a data inicial do backfill.
    set "BACKFILL_EXIT=1"
    goto :BACKFILL_COMPLETO_DONE
)
if not defined BACKFILL_DATA_FIM (
    echo.
    echo [ERRO] Nao foi possivel calcular a data final do backfill.
    set "BACKFILL_EXIT=1"
    goto :BACKFILL_COMPLETO_DONE
)

echo.
echo ================================================================
echo BACKFILL COMPLETO
echo ================================================================
echo Periodo: !BACKFILL_DATA_INICIO! a !BACKFILL_DATA_FIM!
echo Escopo: todas as APIs e entidades habilitadas
echo Schema: pre-existente, validado em modo somente leitura
echo ================================================================

call :EXPORT_AUTH_SESSION
set "PREV_SKIP_AUTH_CHECK=%EXTRATOR_SKIP_AUTH_CHECK%"
set "PREV_NONINTERACTIVE=%EXTRATOR_NONINTERACTIVE%"
set "PREV_MENU_CHILD=%EXTRATOR_MENU_CHILD%"
set "EXTRATOR_SKIP_AUTH_CHECK=1"
set "EXTRATOR_NONINTERACTIVE=1"
set "EXTRATOR_MENU_CHILD=1"
call "%SCRIPT_ROOT%04-extracao_por_intervalo.bat" "!BACKFILL_DATA_INICIO!" "!BACKFILL_DATA_FIM!"
set "BACKFILL_EXTRACAO_EXIT=!ERRORLEVEL!"
if defined PREV_SKIP_AUTH_CHECK (
    set "EXTRATOR_SKIP_AUTH_CHECK=%PREV_SKIP_AUTH_CHECK%"
) else (
    set "EXTRATOR_SKIP_AUTH_CHECK="
)
if defined PREV_NONINTERACTIVE (
    set "EXTRATOR_NONINTERACTIVE=%PREV_NONINTERACTIVE%"
) else (
    set "EXTRATOR_NONINTERACTIVE="
)
if defined PREV_MENU_CHILD (
    set "EXTRATOR_MENU_CHILD=%PREV_MENU_CHILD%"
) else (
    set "EXTRATOR_MENU_CHILD="
)

if not "!BACKFILL_EXTRACAO_EXIT!"=="0" (
    set "BACKFILL_EXIT=!BACKFILL_EXTRACAO_EXIT!"
    echo.
    echo [AVISO] Materializacao das fatos BI ignorada porque a extracao retornou codigo !BACKFILL_EXIT!.
) else (
    call :MATERIALIZAR_FATOS_BI_POST_RUN
    set "BACKFILL_EXIT=!ERRORLEVEL!"
)

:BACKFILL_COMPLETO_DONE
if "%BACKFILL_DIRECT%"=="1" (
    set "AUTO_EXIT=!BACKFILL_EXIT!"
    goto :END_WITH_CODE
)
call :WAIT_AFTER_MENU_ACTION "Backfill completo" "!BACKFILL_EXIT!"
goto :MENU

:RUN_01
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_EXTRACAO_COMPLETA "Executar extracao completa"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :EXECUTION_SAFETY_GATE "Executar extracao completa"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :PREPARE_DATABASE
if errorlevel 1 goto :MENU
call :RUN_SCRIPT_AUTHORIZED "01-executar_extracao_completa.bat"
if errorlevel 1 (
    set "RUN_01_EXIT=!ERRORLEVEL!"
    echo.
    echo [AVISO] Materializacao das fatos BI ignorada porque a extracao retornou codigo !RUN_01_EXIT!.
) else (
    call :MATERIALIZAR_FATOS_BI_POST_RUN
    set "RUN_01_EXIT=!ERRORLEVEL!"
)
call :WAIT_AFTER_MENU_ACTION "Extracao completa" "!RUN_01_EXIT!"
goto :MENU

:RUN_02
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_TESTAR_API "Testar API especifica"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :EXECUTION_SAFETY_GATE "Testar API especifica"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :RUN_SCRIPT_AUTHORIZED "02-testar_api_especifica.bat"
goto :MENU

:RUN_03
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_VALIDAR_CONFIG "Validar configuracoes"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :RUN_SCRIPT_AUTHORIZED "03-validar_config.bat"
goto :MENU

:RUN_04
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_EXTRACAO_INTERVALO "Executar extracao por intervalo"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :EXECUTION_SAFETY_GATE "Executar extracao por intervalo"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :PREPARE_DATABASE
if errorlevel 1 goto :MENU
call :RUN_SCRIPT_AUTHORIZED "04-extracao_por_intervalo.bat"
if errorlevel 1 (
    set "RUN_04_EXIT=!ERRORLEVEL!"
    echo.
    echo [AVISO] Materializacao das fatos BI ignorada porque a extracao retornou codigo !RUN_04_EXIT!.
) else (
    call :MATERIALIZAR_FATOS_BI_POST_RUN
    set "RUN_04_EXIT=!ERRORLEVEL!"
)
call :WAIT_AFTER_MENU_ACTION "Extracao por intervalo" "!RUN_04_EXIT!"
goto :MENU

:RUN_11
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_EXTRACAO_INTERVALO "Executar extracao rapida ultimas 24h"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :EXECUTION_SAFETY_GATE "Executar extracao rapida ultimas 24h"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :PREPARE_DATABASE
if errorlevel 1 goto :MENU
for /f "delims=" %%D in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Date).AddDays(-1).ToString('yyyy-MM-dd')"') do set "RAPIDA_DATA_INICIO=%%D"
for /f "delims=" %%D in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "(Get-Date).ToString('yyyy-MM-dd')"') do set "RAPIDA_DATA_FIM=%%D"
if not defined RAPIDA_DATA_INICIO (
    echo.
    echo ERRO: Nao foi possivel calcular a data inicial da extracao rapida.
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
if not defined RAPIDA_DATA_FIM (
    echo.
    echo ERRO: Nao foi possivel calcular a data final da extracao rapida.
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
echo.
echo Executando extracao rapida D-1..D: !RAPIDA_DATA_INICIO! a !RAPIDA_DATA_FIM!
echo Replay/pre-backfill referencial: DESABILITADO
echo Raster: INCLUIDA se habilitada por configuracao/credenciais
call :EXPORT_AUTH_SESSION
set "PREV_SKIP_AUTH_CHECK=%EXTRATOR_SKIP_AUTH_CHECK%"
set "PREV_NONINTERACTIVE=%EXTRATOR_NONINTERACTIVE%"
set "PREV_MENU_CHILD=%EXTRATOR_MENU_CHILD%"
set "EXTRATOR_SKIP_AUTH_CHECK=1"
set "EXTRATOR_NONINTERACTIVE=1"
set "EXTRATOR_MENU_CHILD=1"
call "%SCRIPT_ROOT%04-extracao_por_intervalo.bat" "!RAPIDA_DATA_INICIO!" "!RAPIDA_DATA_FIM!" "--modo-rapido-24h"
if errorlevel 1 (
    set "RUN_11_EXIT=!ERRORLEVEL!"
    echo.
    echo [AVISO] Materializacao das fatos BI ignorada porque a extracao retornou codigo !RUN_11_EXIT!.
) else (
    call :MATERIALIZAR_FATOS_BI_POST_RUN
    set "RUN_11_EXIT=!ERRORLEVEL!"
)
if defined PREV_SKIP_AUTH_CHECK (
    set "EXTRATOR_SKIP_AUTH_CHECK=%PREV_SKIP_AUTH_CHECK%"
) else (
    set "EXTRATOR_SKIP_AUTH_CHECK="
)
if defined PREV_NONINTERACTIVE (
    set "EXTRATOR_NONINTERACTIVE=%PREV_NONINTERACTIVE%"
) else (
    set "EXTRATOR_NONINTERACTIVE="
)
if defined PREV_MENU_CHILD (
    set "EXTRATOR_MENU_CHILD=%PREV_MENU_CHILD%"
) else (
    set "EXTRATOR_MENU_CHILD="
)
call :WAIT_AFTER_MENU_ACTION "Extracao rapida 24h" "!RUN_11_EXIT!"
goto :MENU

:RUN_RETROFIT
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_EXTRACAO_INTERVALO "Executar Retrofit global"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :RETROFIT_SAFETY_GATE
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :PREPARE_DATABASE
if errorlevel 1 goto :MENU

echo.
echo ================================================================
echo RETROFIT GLOBAL
echo ================================================================
echo Executa todas as fontes habilitadas em carga aditiva.
echo Inclui GraphQL, DataExport e Raster quando habilitado.
echo O expurgo de ausentes permanece desabilitado.
echo.
set "RETROFIT_DIAS="
set /p "RETROFIT_DIAS=Dias retroativos [1-3650, ENTER=Cancelar]: " || goto :MENU
set "RETROFIT_DIAS=!RETROFIT_DIAS: =!"
if not defined RETROFIT_DIAS goto :MENU
powershell -NoProfile -ExecutionPolicy Bypass -Command "$value=0; if ([int]::TryParse($env:RETROFIT_DIAS, [ref]$value) -and $value -ge 1 -and $value -le 3650) { exit 0 }; exit 1"
if errorlevel 1 (
    echo [ERRO] Informe um numero inteiro entre 1 e 3650.
    timeout /t 2 /nobreak >nul 2>&1
    goto :RUN_RETROFIT
)

for /f "tokens=1,2 delims=|" %%A in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "$days=[int]$env:RETROFIT_DIAS; $end=(Get-Date).Date; $start=$end.AddDays(-$days); Write-Output ($start.ToString('yyyy-MM-dd') + '|' + $end.ToString('yyyy-MM-dd'))"') do (
    set "RETROFIT_DATA_INICIO=%%A"
    set "RETROFIT_DATA_FIM=%%B"
)
echo.
echo Periodo: !RETROFIT_DATA_INICIO! a !RETROFIT_DATA_FIM!
echo Escopo: todas as fontes e entidades habilitadas
echo.
set "RETROFIT_CONFIRM="
set /p "RETROFIT_CONFIRM=Digite RETROFIT para confirmar: " || goto :MENU
if /i not "!RETROFIT_CONFIRM!"=="RETROFIT" (
    echo [INFO] Retrofit cancelado pelo operador.
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)

echo.
echo Revalidando concorrencia imediatamente antes da execucao...
call :RETROFIT_SAFETY_GATE
if errorlevel 1 goto :MENU
echo [INFO] Acompanhe o progresso em logs\aplicacao\runtime\extrator-esl.log.
echo [INFO] O pre-backfill referencial de coletas pode consultar uma janela maior que o periodo informado.
call :EXPORT_AUTH_SESSION
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --extracao-intervalo "!RETROFIT_DATA_INICIO!" "!RETROFIT_DATA_FIM!" --retrofit
set "RUN_RETROFIT_EXIT=!ERRORLEVEL!"
if "!RUN_RETROFIT_EXIT!"=="0" (
    call :MATERIALIZAR_FATOS_BI_POST_RUN
    set "RUN_RETROFIT_EXIT=!ERRORLEVEL!"
) else (
    echo.
    echo [AVISO] Materializacao ignorada porque o Retrofit retornou codigo !RUN_RETROFIT_EXIT!.
)
call :WAIT_AFTER_MENU_ACTION "Retrofit global" "!RUN_RETROFIT_EXIT!"
goto :MENU

:RUN_05
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
echo [INFO] Materializacao BI integrada ao loop daemon; scheduler paralelo nao sera iniciado.
call :RUN_SCRIPT "05-loop_extracao_30min.bat"
goto :MENU

:RUN_06
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_BATERIA_EXTREMA "Executar bateria extrema e relatorio de saude do ETL"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :EXECUTION_SAFETY_GATE "Executar bateria extrema"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :PREPARE_DATABASE
if errorlevel 1 goto :MENU
call :RUN_SCRIPT_AUTHORIZED "06-relatorio-completo-validacao.bat"
goto :MENU

:RUN_07
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_EXPORTAR_CSV "Exportar dados para CSV"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :PREPARE_DATABASE
if errorlevel 1 goto :MENU
call :RUN_SCRIPT_AUTHORIZED "07-exportar_csv.bat"
goto :MENU

:RUN_08
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_AUDITORIA_API "Auditar estrutura das APIs"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
call :RUN_SCRIPT_AUTHORIZED "08-auditar_api.bat"
goto :MENU

:RUN_09
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :RUN_SCRIPT "09-gerenciar_usuarios.bat"
goto :MENU

:RUN_AJUDA
call :PREPARE_SECURITY
if errorlevel 1 goto :MENU
call :AUTH_CHECK RUN_AJUDA "Visualizar ajuda"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
echo Executando: java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --ajuda
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --ajuda
echo.
pause
goto :MENU

:TRY_EXIT
goto :END

:PREPARE_SECURITY
if "%BASICS_READY%"=="1" exit /b 0
call :ensure_java
call :check_jar
if errorlevel 1 exit /b 1

call :check_sqlite_auth_db
if errorlevel 1 exit /b 1

set "BASICS_READY=1"
exit /b 0

:RETROFIT_SAFETY_GATE
if not exist "%SCRIPT_ROOT%verificar_execucao_ativa.ps1" (
    echo.
    echo [ERRO] Verificador de execucao ativa nao encontrado:
    echo   %SCRIPT_ROOT%verificar_execucao_ativa.ps1
    exit /b 1
)

call :RUN_EXECUTION_SAFETY_CHECK
if "%SAFETY_EXIT%"=="0" (
    echo.
    echo [OK] Daemon parado e nenhuma execucao concorrente detectada.
    exit /b 0
)
if "%SAFETY_EXIT%"=="2" (
    echo.
    echo [BLOQUEIO] Retrofit cancelado: existe execucao ativa.
    echo [INFO] Pare o Daemon manualmente pela opcao 02 e confirme o estado STOPPED.
    exit /b 1
)

echo.
echo [ERRO] Falha ao validar o estado do Daemon ^(codigo %SAFETY_EXIT%^).
exit /b 1

:EXECUTION_SAFETY_GATE
set "SAFETY_CONTEXT=%~1"
if not defined SAFETY_CONTEXT set "SAFETY_CONTEXT=execucao"

if /i "%EXTRATOR_ALLOW_CONCURRENT_RUN%"=="1" (
    echo.
    echo [AVISO] Trava de execucao paralela ignorada por EXTRATOR_ALLOW_CONCURRENT_RUN=1.
    exit /b 0
)

if not exist "%SCRIPT_ROOT%verificar_execucao_ativa.ps1" (
    echo.
    echo ERRO: Verificador de execucao ativa nao encontrado:
    echo   %SCRIPT_ROOT%verificar_execucao_ativa.ps1
    echo.
    exit /b 1
)

call :RUN_EXECUTION_SAFETY_CHECK
if "%SAFETY_EXIT%"=="0" exit /b 0
if not "%SAFETY_EXIT%"=="2" (
    echo.
    echo ERRO: Falha ao verificar execucoes ativas ^(codigo %SAFETY_EXIT%^).
    exit /b 1
)

if "%PROD_NONINTERACTIVE%"=="1" (
    echo.
    echo [BLOQUEIO] %SAFETY_CONTEXT% cancelada: existe outra execucao ativa.
    echo [INFO] Para automacoes controladas, defina EXTRATOR_ALLOW_CONCURRENT_RUN=1.
    exit /b 1
)

:EXECUTION_SAFETY_CHOICE
echo.
echo O que deseja fazer agora?
echo   1. Cancelar agora ^(recomendado^)
echo   2. Parar loop daemon e revalidar
echo   3. Continuar mesmo assim
echo.
set "SAFETY_OP="
set /p "SAFETY_OP=Escolha uma opcao [1-3]: " || exit /b 1
set "SAFETY_OP=%SAFETY_OP: =%"

if "%SAFETY_OP%"=="1" (
    echo.
    echo Execucao cancelada antes de iniciar: %SAFETY_CONTEXT%.
    exit /b 1
)

if "%SAFETY_OP%"=="2" goto :EXECUTION_SAFETY_STOP_DAEMON
if "%SAFETY_OP%"=="3" goto :EXECUTION_SAFETY_FORCE_CONTINUE

echo Opcao invalida.
goto :EXECUTION_SAFETY_CHOICE

:EXECUTION_SAFETY_STOP_DAEMON
echo.
echo Para parar o loop daemon, uma permissao especifica sera validada.
call :AUTH_CHECK LOOP_STOP "Parar loop daemon"
if errorlevel 1 (
    echo.
    echo Nao foi possivel parar o daemon. Execucao cancelada.
    exit /b 1
)

echo.
echo Solicitando parada do loop daemon...
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-stop
set "SAFETY_STOP_EXIT=!ERRORLEVEL!"
if not "!SAFETY_STOP_EXIT!"=="0" (
    echo.
    echo [AVISO] Comando de parada retornou codigo !SAFETY_STOP_EXIT!.
)

echo.
echo Revalidando execucoes ativas...
timeout /t 3 /nobreak >nul 2>&1
call :RUN_EXECUTION_SAFETY_CHECK
if "%SAFETY_EXIT%"=="0" (
    echo [OK] Nenhuma execucao ativa restante. Continuando: %SAFETY_CONTEXT%.
    exit /b 0
)

echo.
echo Ainda existe execucao ativa. Escolha novamente.
goto :EXECUTION_SAFETY_CHOICE

:EXECUTION_SAFETY_FORCE_CONTINUE
echo.
echo Continuar em paralelo pode causar disputa de lock, lentidao e dados incompletos.
set "SAFETY_CONFIRM="
set /p "SAFETY_CONFIRM=Digite CONTINUAR para seguir mesmo assim: " || exit /b 1
if /i not "%SAFETY_CONFIRM%"=="CONTINUAR" (
    echo Execucao cancelada.
    exit /b 1
)
echo.
echo [AVISO] Execucao paralela autorizada manualmente: %SAFETY_CONTEXT%.
exit /b 0

:RUN_EXECUTION_SAFETY_CHECK
set "SAFETY_EXIT=0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_ROOT%verificar_execucao_ativa.ps1" -RepoRoot "%REPO_ROOT%"
set "SAFETY_EXIT=!ERRORLEVEL!"
exit /b 0

:PREPARE_DATABASE
if "%STARTUP_READY%"=="1" exit /b 0

echo.
echo Validando schema do banco ^(somente leitura^)...
call :VALIDATE_DATABASE_READY
if errorlevel 1 exit /b 1
set "STARTUP_READY=1"
echo [OK] Banco e objetos minimos validados. Nenhuma DDL foi executada.
exit /b 0

:VALIDATE_DATABASE_READY
setlocal EnableExtensions DisableDelayedExpansion
echo   [CHECK] Conexao JDBC, pool e schema minimo de producao...
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --validar
if errorlevel 1 (
    echo [ERRO] Validacao Java/JDBC falhou. Nenhuma correcao automatica sera aplicada.
    endlocal & exit /b 1
)
endlocal & exit /b 0

:MATERIALIZAR_FATOS_BI_POST_RUN
setlocal EnableExtensions DisableDelayedExpansion
echo.
echo ================================================================
echo MATERIALIZAR FATOS BI ^(POST-RUN^)
echo ================================================================
echo Executando: java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --materializar-fatos-bi
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --materializar-fatos-bi
if errorlevel 1 (
    echo [ERRO] Falha na materializacao BI via Java/JDBC.
    endlocal & exit /b 1
)
echo [OK] Fatos BI materializadas no pos-run.
endlocal & exit /b 0

:START_MATERIALIZACAO_BI_SCHEDULER
echo.
echo [INFO] Scheduler isolado de materializacao BI desativado.
echo [INFO] O loop daemon executa a materializacao BI apos cada ciclo de extracao bem-sucedido.
exit /b 0

:check_sqlite_auth_db
if defined EXTRATOR_SECURITY_DB_PATH (
    set "SQLITE_AUTH_DB=!EXTRATOR_SECURITY_DB_PATH!"
) else if defined ProgramData (
    set "SQLITE_AUTH_DB=!ProgramData!\ExtratorESL\security\users.db"
) else (
    set "SQLITE_AUTH_DB=C:\ProgramData\ExtratorESL\security\users.db"
)

if exist "!SQLITE_AUTH_DB!" (
    echo.
    echo [OK] Banco de autenticacao SQLite: !SQLITE_AUTH_DB!
    exit /b 0
)

echo.
echo ================================================================
echo   ERRO: Banco de autenticacao SQLite nao encontrado.
echo.
echo   Caminho verificado: !SQLITE_AUTH_DB!
echo.
echo   Para inicializar o banco de autenticacao, execute:
echo     java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --auth-bootstrap
echo.
echo   Para usar caminho customizado, defina a variavel de ambiente:
echo     EXTRATOR_SECURITY_DB_PATH=C:\caminho\customizado\users.db
echo ================================================================
echo.
if not "%PROD_NONINTERACTIVE%"=="1" pause
exit /b 1

:READ_MENU_OPTION
set "OP="
set /p "OP=Escolha uma opcao [1-13, U=Usuarios, 0=Sair]: " || exit /b 1
set "OP=%OP: =%"
if not defined OP exit /b 2
if "%OP%"=="00" set "OP=0"
if "%OP:~0,1%"=="0" if not "%OP%"=="0" set "OP=%OP:~1%"
if /i "%OP%"=="U" set "OP=11"
for %%V in (0 1 2 3 4 5 6 7 8 9 10 11 12 13) do (
    if "%OP%"=="%%V" exit /b 0
)
exit /b 2

:RUN_SCRIPT
set "TARGET_SCRIPT=%~1"
set "TARGET_ARG1=%~2"
if not defined TARGET_SCRIPT exit /b 1
if not exist "%SCRIPT_ROOT%%TARGET_SCRIPT%" (
    echo.
    echo ERRO: Script "%SCRIPT_ROOT%%TARGET_SCRIPT%" nao encontrado.
    echo.
    if not "%PROD_NONINTERACTIVE%"=="1" pause
    exit /b 1
)
call :EXPORT_AUTH_SESSION
set "PREV_NONINTERACTIVE=%EXTRATOR_NONINTERACTIVE%"
set "PREV_MENU_CHILD=%EXTRATOR_MENU_CHILD%"
if "%PROD_NONINTERACTIVE%"=="1" set "EXTRATOR_NONINTERACTIVE=1"
set "EXTRATOR_MENU_CHILD=1"
if defined TARGET_ARG1 (
    call "%SCRIPT_ROOT%%TARGET_SCRIPT%" "%TARGET_ARG1%"
) else (
    call "%SCRIPT_ROOT%%TARGET_SCRIPT%"
)
set "TARGET_EXIT=!ERRORLEVEL!"
if defined PREV_NONINTERACTIVE (
    set "EXTRATOR_NONINTERACTIVE=%PREV_NONINTERACTIVE%"
) else (
    set "EXTRATOR_NONINTERACTIVE="
)
if defined PREV_MENU_CHILD (
    set "EXTRATOR_MENU_CHILD=%PREV_MENU_CHILD%"
) else (
    set "EXTRATOR_MENU_CHILD="
)
if not "!TARGET_EXIT!"=="0" (
    echo.
    echo [AVISO] %TARGET_SCRIPT% retornou codigo !TARGET_EXIT!.
    timeout /t 2 /nobreak >nul 2>&1
)
exit /b !TARGET_EXIT!

:WAIT_AFTER_MENU_ACTION
if "%PROD_NONINTERACTIVE%"=="1" exit /b 0
echo.
echo ================================================================
if "%~2"=="0" (
    echo [OK] %~1 finalizada.
) else (
    echo [AVISO] %~1 retornou codigo %~2.
)
echo Pressione qualquer tecla para voltar ao menu principal.
echo Para encerrar o sistema, use a opcao 00 no menu.
echo ================================================================
pause >nul
exit /b 0

:RUN_SCRIPT_AUTHORIZED
set "TARGET_SCRIPT=%~1"
set "TARGET_ARG1=%~2"
set "PREV_SKIP_AUTH_CHECK=%EXTRATOR_SKIP_AUTH_CHECK%"
set "EXTRATOR_SKIP_AUTH_CHECK=1"
if defined TARGET_ARG1 (
    call :RUN_SCRIPT "%TARGET_SCRIPT%" "%TARGET_ARG1%"
) else (
    call :RUN_SCRIPT "%TARGET_SCRIPT%"
)
set "TARGET_EXIT=!ERRORLEVEL!"
if defined PREV_SKIP_AUTH_CHECK (
    set "EXTRATOR_SKIP_AUTH_CHECK=%PREV_SKIP_AUTH_CHECK%"
) else (
    set "EXTRATOR_SKIP_AUTH_CHECK="
)
exit /b !TARGET_EXIT!

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
call :AUTH_SESSION_PERMITS "%~1"
if not errorlevel 1 (
    echo.
    echo [OK] Sessao autenticada reutilizada para: %~2
    exit /b 0
)
echo.
echo Autenticacao obrigatoria para executar esta acao.
set "AUTH_CONTEXT_FILE=%TEMP%\extrator_auth_%RANDOM%_%RANDOM%.ctx"
set "EXTRATOR_AUTH_CONTEXT_FILE=%AUTH_CONTEXT_FILE%"
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --auth-check %~1 "%~2"
set "AUTH_EXIT=!ERRORLEVEL!"
set "EXTRATOR_AUTH_CONTEXT_FILE="
if not "!AUTH_EXIT!"=="0" (
    echo Acesso negado.
    exit /b 1
)
call :LOAD_AUTH_CONTEXT "%AUTH_CONTEXT_FILE%"
if exist "%AUTH_CONTEXT_FILE%" del "%AUTH_CONTEXT_FILE%" >nul 2>&1
exit /b 0

:AUTH_SESSION_PERMITS
if not "%AUTH_SESSION_ACTIVE%"=="1" exit /b 1
set "AUTH_REQUIRED=%~1"
if not defined AUTH_REQUIRED exit /b 1
if not defined AUTH_SESSION_ACTIONS exit /b 1
set "AUTH_ACTIONS_CHECK=;%AUTH_SESSION_ACTIONS%;"
if not "!AUTH_ACTIONS_CHECK:;%AUTH_REQUIRED%;=!"=="!AUTH_ACTIONS_CHECK!" exit /b 0
exit /b 1

:LOAD_AUTH_CONTEXT
set "AUTH_CONTEXT_FILE_PATH=%~1"
if not exist "%AUTH_CONTEXT_FILE_PATH%" exit /b 0
for /f "usebackq tokens=1,* delims==" %%A in ("%AUTH_CONTEXT_FILE_PATH%") do (
    if /i "%%A"=="username" set "AUTH_SESSION_USER=%%B"
    if /i "%%A"=="role" set "AUTH_SESSION_ROLE=%%B"
    if /i "%%A"=="actions" set "AUTH_SESSION_ACTIONS=%%B"
)
if defined AUTH_SESSION_ACTIONS set "AUTH_SESSION_ACTIVE=1"
exit /b 0

:EXPORT_AUTH_SESSION
if "%AUTH_SESSION_ACTIVE%"=="1" (
    set "EXTRATOR_AUTH_SESSION_ACTIVE=1"
    set "EXTRATOR_AUTH_SESSION_USER=%AUTH_SESSION_USER%"
    set "EXTRATOR_AUTH_SESSION_ROLE=%AUTH_SESSION_ROLE%"
    set "EXTRATOR_AUTH_SESSION_ACTIONS=%AUTH_SESSION_ACTIONS%"
) else (
    set "EXTRATOR_AUTH_SESSION_ACTIVE="
    set "EXTRATOR_AUTH_SESSION_USER="
    set "EXTRATOR_AUTH_SESSION_ROLE="
    set "EXTRATOR_AUTH_SESSION_ACTIONS="
)
exit /b 0

:check_jar
if exist "%JAR_PATH%" exit /b 0
echo.
echo ERRO: Arquivo "%JAR_PATH%" nao encontrado.
echo Modo producao exige JAR precompilado.
echo Gere o JAR em outra maquina (build) e copie para a pasta target\.
echo.
if not "%PROD_NONINTERACTIVE%"=="1" pause
exit /b 1

:ensure_java
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
        goto :eof
    )
)
for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-25*" 2^>nul') do (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    goto :eof
)
for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-21*" 2^>nul') do (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    goto :eof
)
for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-17*" 2^>nul') do (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    goto :eof
)
for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-*" 2^>nul') do (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
    set "PATH=%JAVA_HOME%\bin;%PATH%"
    goto :eof
)
goto :eof

:END
popd
endlocal
exit /b 0

:END_WITH_CODE
popd
endlocal & exit /b %AUTO_EXIT%
