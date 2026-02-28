@echo off
setlocal EnableExtensions DisableDelayedExpansion
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
REM   - Gera logs detalhados
REM   - Salva dados no banco configurado
REM ================================================================

echo ================================================================
echo INICIANDO EXTRACAO COMPLETA DE DADOS
echo ================================================================

if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    call "%~dp0mvn.bat" -DskipTests package
    if errorlevel 1 (
        echo ERRO: Compilacao falhou
        echo.
        pause
        exit /b 1
    )
)

if not exist "target\extrator.jar" (
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
        set "JAVA_HOME="
    )
)

REM Carregar variaveis de ambiente do usuario
echo Carregando variaveis de ambiente do usuario...
for /f "delims=" %%A in ('powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('DB_URL', 'User')"') do set "DB_URL=%%A"
for /f "delims=" %%A in ('powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('DB_USER', 'User')"') do set "DB_USER=%%A"
for /f "delims=" %%A in ('powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('DB_PASSWORD', 'User')"') do set "DB_PASSWORD=%%A"
for /f "delims=" %%A in ('powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('API_BASEURL', 'User')"') do set "API_BASEURL=%%A"
for /f "delims=" %%A in ('powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('API_REST_TOKEN', 'User')"') do set "API_REST_TOKEN=%%A"
for /f "delims=" %%A in ('powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('API_GRAPHQL_TOKEN', 'User')"') do set "API_GRAPHQL_TOKEN=%%A"
for /f "delims=" %%A in ('powershell -NoProfile -Command "[System.Environment]::GetEnvironmentVariable('API_DATAEXPORT_TOKEN', 'User')"') do set "API_DATAEXPORT_TOKEN=%%A"

REM Verificar se as variaveis obrigatorias estao configuradas
if not defined DB_URL (
    echo ERRO: Variavel de ambiente DB_URL nao encontrada!
    echo Configure esta variavel nas configuracoes do sistema.
    echo.
    pause
    exit /b 1
)

if not defined DB_USER (
    echo ERRO: Variavel de ambiente DB_USER nao encontrada!
    echo Configure esta variavel nas configuracoes do sistema.
    echo.
    pause
    exit /b 1
)

if not defined DB_PASSWORD (
    echo ERRO: Variavel de ambiente DB_PASSWORD nao encontrada!
    echo Configure esta variavel nas configuracoes do sistema.
    echo.
    pause
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
    echo Executando: java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --fluxo-completo %FLAG_FATURAS_GRAPHQL%
) else (
    echo Executando: java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --fluxo-completo
)
echo.
echo ATENCAO: Este processo pode demorar varios minutos...
echo.

if defined FLAG_FATURAS_GRAPHQL (
    java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --fluxo-completo %FLAG_FATURAS_GRAPHQL%
) else (
    java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --fluxo-completo
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
echo.
pause
set "RET_CODE=%JAVA_EXIT_CODE%"
endlocal & exit /b %RET_CODE%

:AUTH_CHECK
if /i "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" exit /b 0
echo.
echo Autenticacao obrigatoria para executar esta acao.
java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --auth-check %~1 "%~2"
if errorlevel 1 (
    echo Acesso negado.
    echo.
    pause
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
