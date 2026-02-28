@echo off
setlocal EnableExtensions EnableDelayedExpansion

if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 65001 >nul
pushd "%~dp0"

set "PROD_MODE=1"
set "JAR_PATH=%~dp0target\extrator.jar"

call :ensure_java

:MENU
cls
echo ================================================================
echo            MENU DE PRODUCAO - EXTRATOR ESL CLOUD
echo            suporte: lucasmac.dev@gmail.com
echo            by: @valentelucass
echo ================================================================
echo.
echo 01. Extracao completa ultimas 24h
echo 02. Loop de extracao 30 minutos
echo 03. Extracao por intervalo
echo 04. Testar API especifica
echo 05. Validar configuracoes
echo 06. Relatorio completo de validacao
echo 07. Exportar CSV
echo 08. Auditar estrutura das APIs
echo 09. Ver ajuda de comandos
echo 10. Gerenciar usuarios de acesso
echo 00. Sair
echo.
set "OP="
if /i "%EXTRATOR_MENU_STDIN%"=="1" (
  set /p "OP=Escolha uma opcao: " || (
    echo.
    echo Entrada encerrada. Encerrando menu de producao.
    goto :END
  )
) else (
  set /p "OP=Escolha uma opcao: " < CON || (
    echo.
    echo Entrada encerrada. Encerrando menu de producao.
    goto :END
  )
)
set "OP=%OP: =%"

if "%OP%"=="1" goto :RUN_01
if "%OP%"=="2" goto :RUN_05
if "%OP%"=="3" goto :RUN_04
if "%OP%"=="4" goto :RUN_02
if "%OP%"=="5" goto :RUN_03
if "%OP%"=="6" goto :RUN_06
if "%OP%"=="7" goto :RUN_07
if "%OP%"=="8" goto :RUN_08
if "%OP%"=="9" goto :RUN_AJUDA
if "%OP%"=="10" goto :RUN_09
if "%OP%"=="0" goto :TRY_EXIT
if "%OP%"=="00" goto :TRY_EXIT

echo.
echo Opcao invalida.
timeout /t 2 /nobreak >nul 2>&1
goto :MENU

:RUN_01
call :check_jar
if errorlevel 1 goto :END
call "%~dp001-executar_extracao_completa.bat"
goto :MENU

:RUN_02
call :check_jar
if errorlevel 1 goto :END
call "%~dp002-testar_api_especifica.bat"
goto :MENU

:RUN_03
call :check_jar
if errorlevel 1 goto :END
call "%~dp003-validar_config.bat"
goto :MENU

:RUN_04
call :check_jar
if errorlevel 1 goto :END
call "%~dp004-extracao_por_intervalo.bat"
goto :MENU

:RUN_05
call :check_jar
if errorlevel 1 goto :END
call "%~dp005-loop_extracao_30min.bat"
goto :MENU

:RUN_06
call :check_jar
if errorlevel 1 goto :END
call "%~dp006-relatorio-completo-validacao.bat"
goto :MENU

:RUN_07
call :check_jar
if errorlevel 1 goto :END
call "%~dp007-exportar_csv.bat"
goto :MENU

:RUN_08
call :check_jar
if errorlevel 1 goto :END
call "%~dp008-auditar_api.bat"
goto :MENU

:RUN_09
call :check_jar
if errorlevel 1 goto :END
call "%~dp009-gerenciar_usuarios.bat"
goto :MENU

:RUN_AJUDA
call :check_jar
if errorlevel 1 goto :END
call :AUTH_CHECK RUN_AJUDA "Visualizar ajuda"
if errorlevel 1 (
    timeout /t 2 /nobreak >nul 2>&1
    goto :MENU
)
echo Executando: java --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" --ajuda
java --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" --ajuda
echo.
pause
goto :MENU

:TRY_EXIT
if exist "%JAR_PATH%" (
    call :AUTH_CHECK MENU_EXIT "Sair do menu principal"
    if errorlevel 1 (
        timeout /t 2 /nobreak >nul 2>&1
        goto :MENU
    )
)
goto :END

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
echo.
echo Autenticacao obrigatoria para executar esta acao.
java --enable-native-access=ALL-UNNAMED -jar "%JAR_PATH%" --auth-check %~1 "%~2"
if errorlevel 1 (
    echo Acesso negado.
    exit /b 1
)
exit /b 0

:check_jar
if exist "%JAR_PATH%" exit /b 0
echo.
echo ERRO: Arquivo "%JAR_PATH%" nao encontrado.
echo Modo producao exige JAR precompilado.
echo Gere o JAR em outra maquina (build) e copie para a pasta target\.
echo.
pause
exit /b 1

:ensure_java
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
        goto :eof
    )
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
