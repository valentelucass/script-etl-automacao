@echo off
setlocal EnableExtensions DisableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "SCRIPT_DIR=%%~fI"
for %%I in ("%SCRIPT_DIR%\..\..") do set "REPO_ROOT=%%~fI"
set "JAVA_BASE_OPTS=--enable-native-access=ALL-UNNAMED -DETL_BASE_DIR=%REPO_ROOT% -Detl.base.dir=%REPO_ROOT%"
if not defined JAR_PATH set "JAR_PATH=%REPO_ROOT%\target\extrator.jar"
if not defined MVN_CMD set "MVN_CMD=%REPO_ROOT%\mvn.bat"
REM ==[DOC-FILE]===============================================================
REM Arquivo : 01-executar_extracao_completa.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "01-executar extracao completa" para uso operacional.
REM
REM Conecta com:
REM - call: %~dp0mvn.bat
REM - mvn (build/test/execucao Java)
REM - call: :AUTH_CHECK
REM - call: :CONFIGURAR_FATURAS_GRAPHQL
REM - java -jar: target\extrator.jar
REM
REM Fluxo geral:
REM 1) Prepara parametros de periodo e escopo de extracao.
REM 2) Executa o jar com comando alvo.
REM 3) Consolida log e retorno da rodada.
REM
REM Variaveis-chave:
REM - FLAG_FATURAS_GRAPHQL: controle de estado do script.
REM - JAVA_HOME: controle de estado do script.
REM - JAVA_EXIT_CODE: controle de estado do script.
REM - RET_CODE: controle de estado do script.
REM [DOC-FILE-END]===========================================================

set "FLAG_FATURAS_GRAPHQL="

REM ================================================================
REM Script: 01-executar_extracao_completa.bat
REM Finalidade:
REM   Executa a extracao completa de dados de todas as APIs.
REM   Este e o script principal para execucao automatizada.
REM
REM Uso:
REM   01-executar_extracao_completa.bat
REM
REM Funcionalidades:
REM   - Executa extracao de todas as entidades
REM   - Inclui as trilhas DataExport de inventario e sinistros
REM   - Gera logs detalhados
REM   - Salva dados no banco configurado
REM ================================================================

echo ================================================================
echo INICIANDO EXTRACAO COMPLETA DE DADOS
echo ================================================================
echo Escopo operacional desta rodada:
echo   GraphQL   = coletas, fretes, faturas_graphql, usuarios_sistema
echo   DataExport = manifestos, cotacoes, localizacao_cargas, contas_a_pagar, faturas_por_cliente, inventario, sinistros
echo.

if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    call "%MVN_CMD%" -DskipTests package
    if errorlevel 1 (
        echo ERRO: Compilacao falhou
        echo.
        if /i not "%EXTRATOR_NONINTERACTIVE%"=="1" pause
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
    if /i not "%EXTRATOR_NONINTERACTIVE%"=="1" pause
    exit /b 1
)

REM Configurar JAVA_HOME automaticamente (Java 17+)
if not defined JAVA_HOME (
    REM Tenta encontrar JDK moderno no Eclipse Adoptium
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-25*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-21*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
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
    ) else (
        set "JAVA_HOME="
    )
)

REM Carregar variaveis de ambiente do usuario
echo Carregando variaveis de ambiente do usuario...
for /f "tokens=1,* delims==" %%A in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "$keys=@('DB_URL','DB_USER','DB_PASSWORD','API_BASEURL','API_REST_TOKEN','API_GRAPHQL_TOKEN','API_DATAEXPORT_TOKEN'); foreach ($key in $keys) { $value=[System.Environment]::GetEnvironmentVariable($key, 'User'); if ($null -ne $value -and $value -ne '') { Write-Output ($key + '=' + $value) } }"') do set "%%A=%%B"

REM Verificar se as variaveis obrigatorias estao configuradas
if not defined DB_URL (
    echo ERRO: Variavel de ambiente DB_URL nao encontrada!
    echo Configure esta variavel nas configuracoes do sistema.
    echo.
    if /i not "%EXTRATOR_NONINTERACTIVE%"=="1" pause
    exit /b 1
)

if not defined DB_USER (
    echo ERRO: Variavel de ambiente DB_USER nao encontrada!
    echo Configure esta variavel nas configuracoes do sistema.
    echo.
    if /i not "%EXTRATOR_NONINTERACTIVE%"=="1" pause
    exit /b 1
)

if not defined DB_PASSWORD (
    echo ERRO: Variavel de ambiente DB_PASSWORD nao encontrada!
    echo Configure esta variavel nas configuracoes do sistema.
    echo.
    if /i not "%EXTRATOR_NONINTERACTIVE%"=="1" pause
    exit /b 1
)

call :AUTH_CHECK RUN_EXTRACAO_COMPLETA "Executar extracao completa"
if errorlevel 1 (
    endlocal & exit /b 1
)

call :CONFIGURAR_FATURAS_GRAPHQL "%~1"
if errorlevel 1 (
    endlocal & exit /b 1
)

if defined FLAG_FATURAS_GRAPHQL (
    echo Executando: java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --fluxo-completo %FLAG_FATURAS_GRAPHQL%
) else (
    echo Executando: java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --fluxo-completo
)
echo.
echo ATENCAO: Este processo pode demorar varios minutos...
echo.

if defined FLAG_FATURAS_GRAPHQL (
    java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --fluxo-completo %FLAG_FATURAS_GRAPHQL%
) else (
    java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --fluxo-completo
)
set "JAVA_EXIT_CODE=%ERRORLEVEL%"

if "%JAVA_EXIT_CODE%"=="0" (
    echo.
    echo ================================================================
    echo EXTRACAO COMPLETA CONCLUIDA COM SUCESSO!
    echo ================================================================
) else if "%JAVA_EXIT_CODE%"=="2" (
    echo.
    echo ================================================================
    echo EXTRACAO COMPLETA CONCLUIDA COM FALHAS PARCIAIS ^(Exit Code: %JAVA_EXIT_CODE%^)
    echo ================================================================
) else (
    echo.
    echo ================================================================
    echo EXTRACAO FALHOU ^(Exit Code: %JAVA_EXIT_CODE%^)
    echo ================================================================
)

echo.
echo Verifique os logs na pasta 'logs' para mais detalhes.
echo Referencias desta rodada:
echo   - passos de inventario e sinistros aparecerao como trilhas proprias nos logs
echo   - destino BI: vw_inventario_powerbi e vw_sinistros_powerbi
echo.
if /i not "%EXTRATOR_NONINTERACTIVE%"=="1" if /i not "%EXTRATOR_MENU_CHILD%"=="1" pause
set "RET_CODE=%JAVA_EXIT_CODE%"
endlocal & exit /b %RET_CODE%

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
echo.
echo Autenticacao obrigatoria para executar esta acao.
java %JAVA_BASE_OPTS% -jar "%JAR_PATH%" --auth-check %~1 "%~2"
if errorlevel 1 (
    echo Acesso negado.
    echo.
    if /i not "%EXTRATOR_NONINTERACTIVE%"=="1" pause
    exit /b 1
)
exit /b 0

:CONFIGURAR_FATURAS_GRAPHQL
set "FLAG_FATURAS_GRAPHQL="
set /a FATURAS_TENTATIVAS=0

if /i "%~1"=="--sem-faturas-graphql" (
    echo.
    echo Faturas GraphQL: DESABILITADO por parametro informado.
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    exit /b 0
)

if /i "%~1"=="--com-faturas-graphql" (
    echo.
    echo Faturas GraphQL: INCLUIDO por parametro informado.
    set "FLAG_FATURAS_GRAPHQL="
    exit /b 0
)

echo.
echo ================================================================
echo CONFIGURACAO DE FATURAS GRAPHQL
echo ================================================================
echo Esta entidade passa por enriquecimento e pode demorar bastante.
echo.

:PERGUNTAR_FATURAS
set /a FATURAS_TENTATIVAS+=1
set /p INCLUIR_FATURAS="Incluir Faturas GraphQL nesta execucao? (1=Sim, 2=Nao, S/N): "

if /i "%INCLUIR_FATURAS%"=="S" (
    echo Faturas GraphQL: INCLUIDO.
    exit /b 0
)

if "%INCLUIR_FATURAS%"=="1" (
    echo Faturas GraphQL: INCLUIDO.
    exit /b 0
)

if /i "%INCLUIR_FATURAS%"=="N" (
    echo Faturas GraphQL: DESABILITADO.
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    exit /b 0
)

if "%INCLUIR_FATURAS%"=="2" (
    echo Faturas GraphQL: DESABILITADO.
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    exit /b 0
)

echo Opcao invalida. Digite 1, 2, S ou N.
if %FATURAS_TENTATIVAS% GEQ 10 (
    echo ERRO: Numero maximo de tentativas atingido ao configurar Faturas GraphQL.
    exit /b 1
)
goto :PERGUNTAR_FATURAS
