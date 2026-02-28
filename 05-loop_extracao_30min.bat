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

echo ================================================================
echo GERENCIAR LOOP DE EXTRACAO ^(30 minutos^)
echo ================================================================
echo.

pushd "%~dp0"

if /i "%PROD_MODE%"=="1" (
  echo Modo producao: pulando compilacao.
) else (
  call "%~dp0mvn.bat" -q -DskipTests package
  if errorlevel 1 (
    echo ERRO: Compilacao falhou
    popd
    exit /b 1
  )
)

if not exist "%~dp0target\extrator.jar" (
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

if /i "%FLAG_FATURAS_GRAPHQL%"=="--sem-faturas-graphql" (
  echo Iniciando loop daemon com Faturas GraphQL DESABILITADO...
  java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --loop-daemon-start --sem-faturas-graphql
) else (
  echo Iniciando loop daemon com Faturas GraphQL INCLUIDO...
  java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --loop-daemon-start
)
echo.
pause
goto :MENU

:STATUS
call :AUTH_CHECK LOOP_STATUS "Consultar status do loop daemon"
if errorlevel 1 goto :MENU
java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --loop-daemon-status
echo.
pause
goto :MENU

:STOP
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" goto :DO_STOP
echo.
echo Autenticacao obrigatoria para executar esta acao.
java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --auth-check LOOP_STOP "Parar loop daemon"
if errorlevel 1 (
  echo Acesso negado.
  echo.
  pause
  goto :MENU
)
:DO_STOP
java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --loop-daemon-stop
echo.
pause
goto :MENU

:RECONFIG_DAEMON
call :AUTH_CHECK LOOP_RECONFIG "Reconfigurar Faturas GraphQL do loop daemon"
if errorlevel 1 goto :MENU
call :ASK_FATURAS_GRAPHQL_LOOP
if errorlevel 1 goto :MENU

echo Reiniciando loop daemon com nova configuracao de Faturas GraphQL...
java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --loop-daemon-stop >nul 2>&1
if /i "%FLAG_FATURAS_GRAPHQL%"=="--sem-faturas-graphql" (
  java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --loop-daemon-start --sem-faturas-graphql
) else (
  java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --loop-daemon-start
)
echo.
pause
goto :MENU

:TAIL_LOGS
call :AUTH_CHECK LOOP_STATUS "Acompanhar logs do loop"
if errorlevel 1 goto :MENU

if not exist "%~dp0logs\daemon\loop_daemon_console.log" (
  echo Arquivo de log do loop ainda nao encontrado.
  echo Inicie o loop e tente novamente.
  echo.
  pause
  goto :MENU
)

echo.
echo Acompanhando logs em tempo real...
echo Arquivo: %~dp0logs\daemon\loop_daemon_console.log
echo Pressione CTRL+C para encerrar a visualizacao e voltar ao menu.
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Content -Path '%~dp0logs\daemon\loop_daemon_console.log' -Encoding UTF8 -Tail 60 -Wait"
echo.
pause
goto :MENU

:EXIT_LOOP
call :AUTH_CHECK LOOP_EXIT_MENU "Sair do menu de loop"
if errorlevel 1 goto :MENU
goto :END

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
set "AUTH_ACTION=%~1"
if /i "%AUTH_ACTION%"=="LOOP_STATUS" (
  if /i "%LOOP_STATUS_AUTH_CACHE%"=="1" exit /b 0
)
if /i "%AUTH_ACTION%"=="LOOP_EXIT_MENU" (
  if /i "%LOOP_STATUS_AUTH_CACHE%"=="1" exit /b 0
)
echo.
echo Autenticacao obrigatoria para executar esta acao.
java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --auth-check %~1 "%~2"
if errorlevel 1 (
  echo Acesso negado.
  echo.
  pause
  exit /b 1
)
if /i "%AUTH_ACTION%"=="LOOP_START" set "LOOP_STATUS_AUTH_CACHE=1"
if /i "%AUTH_ACTION%"=="LOOP_STATUS" set "LOOP_STATUS_AUTH_CACHE=1"
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
