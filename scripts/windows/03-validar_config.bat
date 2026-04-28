@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "SCRIPT_DIR=%%~fI"
for %%I in ("%SCRIPT_DIR%\..\..") do set "REPO_ROOT=%%~fI"
set "JAVA_BASE_OPTS=--enable-native-access=ALL-UNNAMED -DETL_BASE_DIR=%REPO_ROOT% -Detl.base.dir=%REPO_ROOT%"
if not defined JAR_PATH set "JAR_PATH=%REPO_ROOT%\target\extrator.jar"
if not defined MVN_CMD set "MVN_CMD=%REPO_ROOT%\mvn.bat"
REM ==[DOC-FILE]===============================================================
REM Arquivo : 03-validar_config.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "03-validar config" para uso operacional.
REM
REM Conecta com:
REM - call: %~dp0mvn.bat
REM - mvn (build/test/execucao Java)
REM - call: :AUTH_CHECK
REM - java -jar: target\extrator.jar
REM
REM Fluxo geral:
REM 1) Inicializa contexto de validacao.
REM 2) Executa comandos de verificacao no sistema.
REM 3) Publica resumo de sucesso/erro para o operador.
REM
REM Variaveis-chave:
REM - Variaveis nao mapeadas automaticamente; consulte o script abaixo.
REM [DOC-FILE-END]===========================================================

REM ================================================================
REM Script: 03-validar_configuracoes.bat
REM Finalidade:
REM   Valida as configuracoes do sistema e conectividade com APIs.
REM   Util para diagnosticar problemas antes da extracao.
REM
REM Uso:
REM   03-validar_configuracoes.bat
REM
REM Funcionalidades:
REM   - Testa conectividade com APIs
REM   - Valida configuracoes de banco
REM   - Verifica credenciais
REM ================================================================

echo ================================================================
echo VALIDANDO CONFIGURACOES DO SISTEMA
echo ================================================================
echo Cobertura esperada: GraphQL + DataExport, incluindo inventario e sinistros.
echo Validacao tambem confirma prontidao das views e logs operacionais dessas trilhas.
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

call :AUTH_CHECK RUN_VALIDAR_CONFIG "Validar configuracoes"
if errorlevel 1 exit /b 1

echo Executando: java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --validar
echo.

java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --validar

if !ERRORLEVEL! equ 0 (
    echo.
    echo ================================================================
    echo VALIDACAO CONCLUIDA COM SUCESSO!
    echo ================================================================
) else (
    echo.
    echo ================================================================
    echo VALIDACAO FALHOU ^(Exit Code: !ERRORLEVEL!^)
    echo ================================================================
)

echo.
pause
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
