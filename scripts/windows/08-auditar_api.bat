@echo off
setlocal enableextensions
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "SCRIPT_DIR=%%~fI"
for %%I in ("%SCRIPT_DIR%\..\..") do set "REPO_ROOT=%%~fI"
set "JAVA_BASE_OPTS=--enable-native-access=ALL-UNNAMED -DETL_BASE_DIR=%REPO_ROOT% -Detl.base.dir=%REPO_ROOT%"
if not defined JAR_PATH set "JAR_PATH=%REPO_ROOT%\target\extrator.jar"
if not defined MVN_CMD set "MVN_CMD=%REPO_ROOT%\mvn.bat"
REM ==[DOC-FILE]===============================================================
REM Arquivo : 08-auditar_api.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "08-auditar api" para uso operacional.
REM
REM Conecta com:
REM - call: %~dp0mvn.bat
REM - mvn (build/test/execucao Java)
REM - call: :AUTH_CHECK
REM - java -jar: target\extrator.jar
REM
REM Fluxo geral:
REM 1) Aciona rotina de auditoria tecnica.
REM 2) Coleta evidencias e gera saida de analise.
REM 3) Finaliza com status e orientacao de proximo passo.
REM
REM Variaveis-chave:
REM - JAVA_HOME: controle de estado do script.
REM - EXITCODE: controle de estado do script.
REM [DOC-FILE-END]===========================================================

if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 65001 >nul

REM ================================================================
REM Script: 08-auditar_api.bat
REM Finalidade:
REM   Audita a estrutura das APIs (GraphQL e DataExport).
REM   Gera relatorio CSV com todos os campos disponiveis em cada entidade.
REM   Inclui as trilhas atuais de inventario e sinistros na cobertura DataExport.
REM
REM Uso:
REM   08-auditar_api.bat
REM
REM Funcionalidades:
REM   - Compila o projeto automaticamente
REM   - Executa auditoria de estrutura das APIs
REM   - Gera relatorio CSV na pasta 'relatorios'
REM ================================================================

echo ================================================================
echo AUDITAR ESTRUTURA DA API
echo ================================================================
echo.
echo Cobertura atual:
echo   GraphQL   = coletas, fretes, faturas_graphql, usuarios_sistema
echo   DataExport = manifestos, cotacoes, localizacao_cargas, contas_a_pagar, faturas_por_cliente, inventario, sinistros
echo.

if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    echo Compilando projeto...
    call "%MVN_CMD%" -q -DskipTests package
    if errorlevel 1 (
        echo ERRO: Compilacao falhou
        echo.
        pause
        exit /b 1
    )
)

if not exist "%JAR_PATH%" (
    echo ERRO: Arquivo target\extrator.jar nao encontrado!
    if /i "%PROD_MODE%"=="1" (
        echo Modo producao requer JAR precompilado.
    ) else (
        echo Execute primeiro: mvn package -DskipTests
    )
    echo.
    pause
    exit /b 1
)

REM Configurar JAVA_HOME automaticamente (Java 17+)
if not defined JAVA_HOME (
    REM Tenta encontrar JDK 17+ no Eclipse Adoptium
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-17*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
    REM Se nao encontrar, tenta qualquer JDK 17+ no Adoptium
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
)
:javahomefound
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    ) else (
        echo AVISO: JAVA_HOME configurado, mas java.exe nao encontrado
    )
)

call :AUTH_CHECK RUN_AUDITORIA_API "Auditar estrutura das APIs"
if errorlevel 1 exit /b 1

echo Executando auditor...
echo.

java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --auditar-api
set "EXITCODE=%ERRORLEVEL%"

if %EXITCODE% NEQ 0 (
    echo.
    echo ================================================================
    echo AUDITORIA CONCLUIDA COM ERRO - Exit Code: %EXITCODE%
    echo ================================================================
    echo.
    pause
    endlocal
    exit /b %EXITCODE%
) else (
    echo.
    echo ================================================================
    echo AUDITORIA CONCLUIDA COM SUCESSO!
    echo ================================================================
)

echo.
echo Saida em pasta 'relatorios'.
echo Referencias esperadas no inventario de campos: inventario e sinistros.
echo.
pause
endlocal
exit /b 0

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
echo.
echo Autenticacao obrigatoria para executar esta acao.
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --auth-check %~1 "%~2"
if errorlevel 1 (
    echo Acesso negado.
    echo.
    pause
    exit /b 1
)
exit /b 0
