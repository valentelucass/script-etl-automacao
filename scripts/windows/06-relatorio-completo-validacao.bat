@echo off
setlocal EnableExtensions EnableDelayedExpansion

if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 65001 >nul

echo ================================================================
echo BATERIA EXTREMA E RELATORIO DE SAUDE DO ETL
echo ================================================================
echo.
echo Este script executa a validacao completa do ETL sem alterar producao
echo por padrao. Testes que reescrevem dados ^(idempotencia e hidratacao^)
echo so rodam com confirmacao explicita.
echo.

if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    echo Compilando projeto...
    call "%~dp0mvn.bat" -q -DskipTests package
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
    )
    echo.
    pause
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

call :AUTH_CHECK RUN_BATERIA_EXTREMA "Executar bateria extrema e relatorio de saude do ETL"
if errorlevel 1 exit /b 1

call :CONFIGURAR_FATURAS_GRAPHQL
if errorlevel 1 exit /b 1

call :CONFIGURAR_IDEMPOTENCIA
if errorlevel 1 exit /b 1

call :CONFIGURAR_HIDRATACAO_ORFAOS
if errorlevel 1 exit /b 1

call :CONFIGURAR_STRESS
if errorlevel 1 exit /b 1

set "CMD_FLAGS=--periodo-fechado --permitir-fallback-janela --stress-repeticoes %STRESS_REPETICOES%"
if defined FLAG_SEM_FATURAS_GRAPHQL set "CMD_FLAGS=!CMD_FLAGS! --sem-faturas-graphql"
if defined FLAG_EXECUTAR_IDEMPOTENCIA set "CMD_FLAGS=!CMD_FLAGS! --executar-idempotencia"
if defined FLAG_EXECUTAR_HIDRATACAO set "CMD_FLAGS=!CMD_FLAGS! --executar-hidratacao-orfaos"

echo.
echo Executando: java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --validar-etl-extremo !CMD_FLAGS!
echo.
java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --validar-etl-extremo !CMD_FLAGS!
set "RET_CODE=%ERRORLEVEL%"

echo.
if "%RET_CODE%"=="0" (
    echo ================================================================
    echo BATERIA EXTREMA CONCLUIDA COM SUCESSO
    echo ================================================================
) else (
    echo ================================================================
    echo BATERIA EXTREMA IDENTIFICOU PROBLEMAS ^(Exit Code: %RET_CODE%^)
    echo ================================================================
)
echo.
echo Verifique os relatorios gerados na pasta logs\.
echo.
pause
endlocal & exit /b %RET_CODE%

:CONFIGURAR_FATURAS_GRAPHQL
set "FLAG_SEM_FATURAS_GRAPHQL="
echo Incluir Faturas GraphQL na bateria? ^(1=Sim, 2=Nao^)
set /p "OP_FATURAS=> " || exit /b 1
set "OP_FATURAS=%OP_FATURAS: =%"
if "%OP_FATURAS%"=="1" exit /b 0
if "%OP_FATURAS%"=="2" (
    set "FLAG_SEM_FATURAS_GRAPHQL=1"
    exit /b 0
)
echo Opcao invalida.
exit /b 1

:CONFIGURAR_IDEMPOTENCIA
set "FLAG_EXECUTAR_IDEMPOTENCIA="
echo.
echo Executar teste de idempotencia? ^(roda o ETL duas vezes e regrava dados^)
echo 1=Sim, 2=Nao ^(recomendado em producao^)
set /p "OP_IDEMPOTENCIA=> " || exit /b 1
set "OP_IDEMPOTENCIA=%OP_IDEMPOTENCIA: =%"
if "%OP_IDEMPOTENCIA%"=="2" exit /b 0
if "%OP_IDEMPOTENCIA%"=="1" (
    set "FLAG_EXECUTAR_IDEMPOTENCIA=1"
    exit /b 0
)
echo Opcao invalida.
exit /b 1

:CONFIGURAR_HIDRATACAO_ORFAOS
set "FLAG_EXECUTAR_HIDRATACAO="
echo.
echo Executar hidratacao de orfaos? ^(pode gravar coletas de apoio no banco^)
echo 1=Sim, 2=Nao ^(recomendado em producao^)
set /p "OP_HIDRATACAO=> " || exit /b 1
set "OP_HIDRATACAO=%OP_HIDRATACAO: =%"
if "%OP_HIDRATACAO%"=="2" exit /b 0
if "%OP_HIDRATACAO%"=="1" (
    set "FLAG_EXECUTAR_HIDRATACAO=1"
    exit /b 0
)
echo Opcao invalida.
exit /b 1

:CONFIGURAR_STRESS
set "STRESS_REPETICOES=3"
echo.
set /p "STRESS_REPETICOES=Numero de repeticoes do teste de stress [padrao 3]: " || exit /b 0
set "STRESS_REPETICOES=%STRESS_REPETICOES: =%"
if not defined STRESS_REPETICOES set "STRESS_REPETICOES=3"
for /f "delims=0123456789" %%A in ("%STRESS_REPETICOES%") do (
    echo Valor invalido para stress.
    exit /b 1
)
if %STRESS_REPETICOES% LSS 1 set "STRESS_REPETICOES=1"
exit /b 0

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
