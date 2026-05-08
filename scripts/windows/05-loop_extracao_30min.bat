@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM ==[DOC-FILE]===============================================================
REM Arquivo : 05-loop_extracao_30min.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "05-loop extracao 30min" para uso operacional.
REM
REM Conecta com:
REM - call: %~dp0mvn.bat
REM - call: :AUTH_CHECK
REM - call: :ASK_FATURAS_GRAPHQL_LOOP
REM - java -jar: %~dp0target\extrator.jar
REM
REM Fluxo geral:
REM 1) Prepara parametros de periodo e escopo de extracao.
REM 2) Executa o jar com comando alvo.
REM 3) Consolida log e retorno da rodada.
REM
REM Variaveis-chave:
REM - JAVA_HOME: controle de estado do script.
REM - LOOP_STATUS_AUTH_CACHE: controle de estado do script.
REM - OP: controle de estado do script.
REM - AUTH_ACTION: controle de estado do script.
REM - FLAG_FATURAS_GRAPHQL: controle de estado do script.
REM - OP_FATURAS: controle de estado do script.
REM [DOC-FILE-END]===========================================================

REM Em cmd.exe, set /p com entrada redirecionada falha com CP 65001.
REM CP 1252 preserva compatibilidade em execucao interativa e automatizada.
if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 1252 >nul

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "SCRIPT_DIR=%%~fI"
for %%I in ("%SCRIPT_DIR%\..\..") do set "REPO_ROOT=%%~fI"
set "JAVA_BASE_OPTS=--enable-native-access=ALL-UNNAMED -DETL_BASE_DIR=%REPO_ROOT% -Detl.base.dir=%REPO_ROOT%"
set "JAR_PATH=%REPO_ROOT%\target\extrator.jar"
set "LOOP_LOG=%REPO_ROOT%\logs\daemon\runtime\loop_daemon_console.log"

echo ================================================================
echo GERENCIAR LOOP DE EXTRACAO ^(30 minutos^)
echo ================================================================
echo.
echo Cobertura do loop:
echo   GraphQL   = coletas, fretes, faturas_graphql, usuarios_sistema
echo   DataExport = manifestos, cotacoes, localizacao_cargas, contas_a_pagar, faturas_por_cliente, inventario, sinistros
echo   Raster    = raster_viagens e raster_viagem_paradas ^(quando RASTER_ENABLED/credenciais habilitarem^)
echo.

pushd "%REPO_ROOT%"

if /i "%PROD_MODE%"=="1" (
  echo Modo producao: pulando compilacao.
) else (
  call "%REPO_ROOT%\mvn.bat" -q -DskipTests package
  if errorlevel 1 (
    echo ERRO: Compilacao falhou
    popd
    exit /b 1
  )
)

if not exist "%JAR_PATH%" (
  echo ERRO: target\extrator.jar nao encontrado
  if /i "%PROD_MODE%"=="1" (
    echo Modo producao requer JAR precompilado.
  )
  popd
  exit /b 1
)

if not defined JAVA_HOME (
  for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-17*" 2^>nul') do (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
    goto :javahomefound
  )
  for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-*" 2^>nul') do (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
    goto :javahomefound
  )
)
:javahomefound
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "PATH=%JAVA_HOME%\bin;%PATH%"
  )
)
set "LOOP_STATUS_AUTH_CACHE=0"
set "AUTH_SESSION_ACTIVE=0"
set "AUTH_SESSION_USER="
set "AUTH_SESSION_ROLE="
set "AUTH_SESSION_ACTIONS="
if /i "%EXTRATOR_AUTH_SESSION_ACTIVE%"=="1" (
  set "AUTH_SESSION_ACTIVE=1"
  set "AUTH_SESSION_USER=%EXTRATOR_AUTH_SESSION_USER%"
  set "AUTH_SESSION_ROLE=%EXTRATOR_AUTH_SESSION_ROLE%"
  set "AUTH_SESSION_ACTIONS=%EXTRATOR_AUTH_SESSION_ACTIONS%"
)

:MENU
echo.
echo ================================================================
echo MENU LOOP
echo ================================================================
echo 01. Iniciar loop de extracao ^(segundo plano, continua mesmo fechando terminal^)
echo 02. Status do loop de extracao
echo 03. Parar loop de extracao
echo 04. Reconfigurar Faturas GraphQL do loop ^(reinicia loop^)
echo 05. Acompanhar logs da extracao ^(tempo real^)
echo 00. Voltar
echo.
set "OP="
set /p OP="Escolha uma opcao: " || (
  echo.
  echo Entrada encerrada. Encerrando menu do loop.
  goto :END
)
set "OP=%OP: =%"

if "%OP%"=="1" goto :START
if "%OP%"=="01" goto :START
if "%OP%"=="2" goto :STATUS
if "%OP%"=="02" goto :STATUS
if "%OP%"=="3" goto :STOP
if "%OP%"=="03" goto :STOP
if "%OP%"=="4" goto :RECONFIG_DAEMON
if "%OP%"=="04" goto :RECONFIG_DAEMON
if "%OP%"=="5" goto :TAIL_LOGS
if "%OP%"=="05" goto :TAIL_LOGS
if "%OP%"=="0" goto :EXIT_LOOP
if "%OP%"=="00" goto :EXIT_LOOP

echo Opcao invalida.
timeout /t 2 /nobreak >nul 2>&1
goto :MENU

:START
call :AUTH_CHECK LOOP_START "Iniciar loop daemon"
if errorlevel 1 goto :MENU
call :ASK_FATURAS_GRAPHQL_LOOP
if errorlevel 1 goto :MENU
call :EXECUTION_SAFETY_GATE "Iniciar loop daemon"
if errorlevel 1 goto :MENU

if /i "%FLAG_FATURAS_GRAPHQL%"=="--sem-faturas-graphql" (
  echo Iniciando loop daemon com Faturas GraphQL DESABILITADO...
  echo As trilhas DataExport de inventario/sinistros e Raster habilitada permanecem ativas no loop.
  java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-start --sem-faturas-graphql
) else (
  echo Iniciando loop daemon com Faturas GraphQL INCLUIDO...
  echo As trilhas DataExport de inventario/sinistros e Raster habilitada permanecem ativas no loop.
  java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-start
)
echo.
pause
goto :MENU

:STATUS
call :AUTH_CHECK LOOP_STATUS "Consultar status do loop daemon"
if errorlevel 1 goto :MENU
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-status
echo.
pause
goto :MENU

:STOP
call :AUTH_CHECK LOOP_STOP "Parar loop daemon"
if errorlevel 1 goto :MENU
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-stop
echo.
pause
goto :MENU

:RECONFIG_DAEMON
call :AUTH_CHECK LOOP_RECONFIG "Reconfigurar Faturas GraphQL do loop daemon"
if errorlevel 1 goto :MENU
call :ASK_FATURAS_GRAPHQL_LOOP
if errorlevel 1 goto :MENU

echo Reiniciando loop daemon com nova configuracao de Faturas GraphQL...
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-stop >nul 2>&1
if /i "%FLAG_FATURAS_GRAPHQL%"=="--sem-faturas-graphql" (
  java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-start --sem-faturas-graphql
) else (
  java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-start
)
echo.
pause
goto :MENU

:TAIL_LOGS
call :AUTH_CHECK LOOP_STATUS "Acompanhar logs do loop"
if errorlevel 1 goto :MENU

if not exist "%LOOP_LOG%" (
  echo Arquivo de log do loop ainda nao encontrado.
  echo Inicie o loop e tente novamente.
  echo.
  pause
  goto :MENU
)

echo.
echo Acompanhando logs em tempo real...
echo Arquivo: %LOOP_LOG%
echo Referencias esperadas: dataexport:inventario, dataexport:sinistros, raster:raster_viagens e raster_viagem_paradas quando habilitada.
echo Pressione CTRL+C para encerrar a visualizacao e voltar ao menu.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Content -Path '%LOOP_LOG%' -Encoding UTF8 -Tail 60 -Wait"
echo.
pause
goto :MENU

:EXIT_LOOP
goto :END

:EXECUTION_SAFETY_GATE
set "SAFETY_CONTEXT=%~1"
if not defined SAFETY_CONTEXT set "SAFETY_CONTEXT=execucao"

if /i "%EXTRATOR_ALLOW_CONCURRENT_RUN%"=="1" (
  echo.
  echo [AVISO] Trava de execucao paralela ignorada por EXTRATOR_ALLOW_CONCURRENT_RUN=1.
  exit /b 0
)

if not exist "%SCRIPT_DIR%\verificar_execucao_ativa.ps1" (
  echo.
  echo ERRO: Verificador de execucao ativa nao encontrado:
  echo   %SCRIPT_DIR%\verificar_execucao_ativa.ps1
  echo.
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%\verificar_execucao_ativa.ps1" -RepoRoot "%REPO_ROOT%"
set "SAFETY_EXIT=%ERRORLEVEL%"
if "%SAFETY_EXIT%"=="0" exit /b 0
if not "%SAFETY_EXIT%"=="2" (
  echo.
  echo ERRO: Falha ao verificar execucoes ativas ^(codigo %SAFETY_EXIT%^).
  exit /b 1
)

if /i "%EXTRATOR_NONINTERACTIVE%"=="1" (
  echo.
  echo [BLOQUEIO] %SAFETY_CONTEXT% cancelada: existe outra execucao ativa.
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
echo Solicitando parada do loop daemon e de steps isolados vinculados...
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --loop-daemon-stop
set "SAFETY_STOP_EXIT=!ERRORLEVEL!"
if not "!SAFETY_STOP_EXIT!"=="0" (
  echo.
  echo [AVISO] Comando de parada retornou codigo !SAFETY_STOP_EXIT!.
)

echo.
echo Revalidando execucoes ativas...
timeout /t 3 /nobreak >nul 2>&1
powershell -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%\verificar_execucao_ativa.ps1" -RepoRoot "%REPO_ROOT%"
set "SAFETY_EXIT=%ERRORLEVEL%"
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

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
set "AUTH_ACTION=%~1"
call :AUTH_SESSION_PERMITS "%AUTH_ACTION%"
if not errorlevel 1 (
  echo.
  echo [OK] Sessao autenticada reutilizada para: %~2
  exit /b 0
)
if /i "%AUTH_ACTION%"=="LOOP_STATUS" (
  if /i "%LOOP_STATUS_AUTH_CACHE%"=="1" exit /b 0
)
echo.
echo Autenticacao obrigatoria para executar esta acao.
set "AUTH_CONTEXT_FILE=%TEMP%\extrator_auth_loop_%RANDOM%_%RANDOM%.ctx"
set "EXTRATOR_AUTH_CONTEXT_FILE=%AUTH_CONTEXT_FILE%"
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --auth-check %~1 "%~2"
set "AUTH_EXIT=!ERRORLEVEL!"
set "EXTRATOR_AUTH_CONTEXT_FILE="
if not "!AUTH_EXIT!"=="0" (
  echo Acesso negado.
  echo.
  pause
  exit /b 1
)
call :LOAD_AUTH_CONTEXT "%AUTH_CONTEXT_FILE%"
if exist "%AUTH_CONTEXT_FILE%" del "%AUTH_CONTEXT_FILE%" >nul 2>&1
if /i "%AUTH_ACTION%"=="LOOP_START" set "LOOP_STATUS_AUTH_CACHE=1"
if /i "%AUTH_ACTION%"=="LOOP_STATUS" set "LOOP_STATUS_AUTH_CACHE=1"
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

:ASK_FATURAS_GRAPHQL_LOOP
set "FLAG_FATURAS_GRAPHQL="
echo.
echo ================================================================
echo CONFIGURAR FATURAS GRAPHQL NO LOOP
echo ================================================================
echo 01. Incluir Faturas GraphQL
echo 02. Desabilitar Faturas GraphQL ^(--sem-faturas-graphql^)
echo 00. Cancelar inicio do loop
echo.
set "OP_FATURAS="
set /p OP_FATURAS="Escolha uma opcao: " || (
  echo Entrada encerrada. Cancelando configuracao de Faturas GraphQL.
  exit /b 1
)
set "OP_FATURAS=%OP_FATURAS: =%"

if "%OP_FATURAS%"=="1" (
  set "FLAG_FATURAS_GRAPHQL="
  exit /b 0
)
if "%OP_FATURAS%"=="01" (
  set "FLAG_FATURAS_GRAPHQL="
  exit /b 0
)
if "%OP_FATURAS%"=="2" (
  set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
  exit /b 0
)
if "%OP_FATURAS%"=="02" (
  set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
  exit /b 0
)
if "%OP_FATURAS%"=="0" (
  echo Inicio do loop cancelado.
  exit /b 1
)
if "%OP_FATURAS%"=="00" (
  echo Inicio do loop cancelado.
  exit /b 1
)

echo Opcao invalida.
timeout /t 2 /nobreak >nul 2>&1
goto :ASK_FATURAS_GRAPHQL_LOOP

:END
popd
endlocal
exit /b 0
