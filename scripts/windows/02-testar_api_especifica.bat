@echo off
setlocal EnableDelayedExpansion
REM ==[DOC-FILE]===============================================================
REM Arquivo : 02-testar_api_especifica.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "02-testar api especifica" para uso operacional.
REM
REM Conecta com:
REM - call: %~dp0mvn.bat
REM - mvn (build/test/execucao Java)
REM - call: :AUTH_CHECK
REM - java -jar: target\extrator.jar
REM
REM Fluxo geral:
REM 1) Valida argumentos ou abre menu interativo.
REM 2) Monta o comando Java para API/entidade escolhida.
REM 3) Executa o teste e retorna codigo de status.
REM
REM Variaveis-chave:
REM - FLAG_FATURAS_GRAPHQL: controle de estado do script.
REM - API: controle de estado do script.
REM - ENTIDADE: controle de estado do script.
REM - JAVA_HOME: controle de estado do script.
REM - CMD_ARGS: controle de estado do script.
REM [DOC-FILE-END]===========================================================

REM Ajusta code page para evitar erros de parsing com acentos e parenteses
if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 1252 >nul
set "FLAG_FATURAS_GRAPHQL="

REM ================================================================
REM Script: 02-testar_api_especifica.bat
REM Finalidade:
REM   Executa testes da API especifica informada como parametro ou via menu interativo.
REM   Valores aceitos: 'graphql' ou 'dataexport'.
REM
REM Uso:
REM   02-testar_api_especifica.bat [api] [entidade] [--sem-faturas-graphql]
REM
REM Parametros (opcionais):
REM   %1  Nome da API a testar: graphql | dataexport
REM   %2  Entidade (opcional):
REM       GraphQL -> coletas | fretes | faturas_graphql
REM       DataExport -> manifestos | cotacoes | localizacao_carga | contas_a_pagar | faturas_por_cliente
REM   %3  Flag opcional: --sem-faturas-graphql (somente para GraphQL sem entidade especifica)
REM
REM Se nenhum parametro for informado, exibe menu interativo.
REM ================================================================

REM Se parametros foram fornecidos, usar diretamente
if not "%~1"=="" (
    set "API=%~1"
    set "ENTIDADE=%~2"
    set "FLAG_FATURAS_GRAPHQL=%~3"
    if /i "!ENTIDADE!"=="--sem-faturas-graphql" (
        set "ENTIDADE="
        set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    )
    if not "%~4"=="" (
        echo ERRO: Parametros em excesso.
        echo.
        echo Uso: 02-testar_api_especifica.bat [api] [entidade] [--sem-faturas-graphql]
        echo.
        pause
        exit /b 1
    )
    goto :VALIDATE_ARGS
)

REM ================================================================
REM MENU INTERATIVO
REM ================================================================
:INTERACTIVE_MENU
cls
echo ================================================================
echo TESTE DE API ESPECIFICA
echo ================================================================
echo.
echo Escolha a API:
echo   1. GraphQL
echo   2. DataExport
echo.
set /p "OPCAO_API=Digite sua opcao (1 ou 2): "

if "%OPCAO_API%"=="1" (
    set "API=graphql"
    goto :CHOOSE_ENTITY
)
if "%OPCAO_API%"=="2" (
    set "API=dataexport"
    goto :CHOOSE_ENTITY
)

echo.
echo ERRO: Opcao invalida!
timeout /t 2 /nobreak >nul 2>&1
goto :INTERACTIVE_MENU

:CHOOSE_ENTITY
cls
echo ================================================================
echo TESTE DE API: %API%
echo ================================================================
echo.
echo Escolha o escopo:
echo   1. Todas as entidades
echo   2. Entidade especifica
echo.
set /p "OPCAO_ESCOPO=Digite sua opcao (1 ou 2): "

if "%OPCAO_ESCOPO%"=="1" (
    set "ENTIDADE="
    if /i "%API%"=="graphql" goto :CONFIGURAR_FATURAS_GRAPHQL
    goto :RUN
)
if "%OPCAO_ESCOPO%"=="2" (
    goto :CHOOSE_SPECIFIC_ENTITY
)

echo.
echo ERRO: Opcao invalida!
timeout /t 2 /nobreak >nul 2>&1
goto :CHOOSE_ENTITY

:CHOOSE_SPECIFIC_ENTITY
cls
echo ================================================================
echo TESTE DE API: %API% - ENTIDADE ESPECIFICA
echo ================================================================
echo.

if /i "%API%"=="graphql" (
    echo Entidades disponiveis:
    echo   1. Coletas
    echo   2. Fretes
    echo   3. Faturas GraphQL
    echo   4. Usuarios do Sistema
    echo.
    set /p "OPCAO_ENTIDADE=Digite sua opcao ^(1, 2, 3 ou 4^): "
    
    if "!OPCAO_ENTIDADE!"=="1" set "ENTIDADE=coletas"
    if "!OPCAO_ENTIDADE!"=="2" set "ENTIDADE=fretes"
    if "!OPCAO_ENTIDADE!"=="3" set "ENTIDADE=faturas_graphql"
    if "!OPCAO_ENTIDADE!"=="4" set "ENTIDADE=usuarios_sistema"
    
    if "!ENTIDADE!"=="" (
        echo.
        echo ERRO: Opcao invalida!
        timeout /t 2 /nobreak >nul 2>&1
        goto :CHOOSE_SPECIFIC_ENTITY
    )
    goto :RUN
)

if /i "%API%"=="dataexport" (
    echo Entidades disponiveis:
    echo   1. Manifestos
    echo   2. Cotacoes
    echo   3. Localizacao de Carga
    echo   4. Contas a Pagar
    echo   5. Faturas por Cliente
    echo.
    set /p "OPCAO_ENTIDADE=Digite sua opcao ^(1, 2, 3, 4 ou 5^): "
    
    if "!OPCAO_ENTIDADE!"=="1" set "ENTIDADE=manifestos"
    if "!OPCAO_ENTIDADE!"=="2" set "ENTIDADE=cotacoes"
    if "!OPCAO_ENTIDADE!"=="3" set "ENTIDADE=localizacao_carga"
    if "!OPCAO_ENTIDADE!"=="4" set "ENTIDADE=contas_a_pagar"
    if "!OPCAO_ENTIDADE!"=="5" set "ENTIDADE=faturas_por_cliente"
    
    if "!ENTIDADE!"=="" (
        echo.
        echo ERRO: Opcao invalida!
        timeout /t 2 /nobreak >nul 2>&1
        goto :CHOOSE_SPECIFIC_ENTITY
    )
    goto :RUN
)

goto :RUN

:VALIDATE_ARGS
REM Valida API e entidade usando ramos sem parenteses
if /i "%API%"=="graphql" goto :CHECK_GRAPHQL
if /i "%API%"=="dataexport" goto :CHECK_DATAEXPORT
echo ERRO: API '%API%' nao reconhecida!
echo.
echo APIs suportadas: graphql, dataexport
echo.
pause
exit /b 1

:CHECK_GRAPHQL
if "%ENTIDADE%"=="" goto :RUN
if /i "%ENTIDADE%"=="coletas" goto :RUN
if /i "%ENTIDADE%"=="fretes" goto :RUN
if /i "%ENTIDADE%"=="faturas_graphql" goto :RUN
if /i "%ENTIDADE%"=="faturas" goto :RUN
if /i "%ENTIDADE%"=="usuarios_sistema" goto :RUN
if /i "%ENTIDADE%"=="usuarios" goto :RUN
echo ERRO: Entidade '%ENTIDADE%' invalida para API GraphQL!
echo.
echo Entidades suportadas: coletas, fretes, faturas_graphql, faturas, usuarios_sistema, usuarios
echo.
pause
exit /b 1

:CHECK_DATAEXPORT
if "%ENTIDADE%"=="" goto :RUN
if /i "%ENTIDADE%"=="manifestos" goto :RUN
if /i "%ENTIDADE%"=="cotacoes" goto :RUN
if /i "%ENTIDADE%"=="localizacao_carga" goto :RUN
if /i "%ENTIDADE%"=="localizacao_de_carga" goto :RUN
if /i "%ENTIDADE%"=="contas_a_pagar" goto :RUN
if /i "%ENTIDADE%"=="contasapagar" goto :RUN
if /i "%ENTIDADE%"=="faturas_por_cliente" goto :RUN
if /i "%ENTIDADE%"=="faturasporcliente" goto :RUN
echo ERRO: Entidade '%ENTIDADE%' invalida para API DataExport!
echo.
echo Entidades suportadas: manifestos, cotacoes, localizacao_carga, contas_a_pagar, faturas_por_cliente
echo.
pause
exit /b 1

:RUN
cls
echo ================================================================
echo TESTANDO API: %API%
if not "%ENTIDADE%"=="" echo ENTIDADE: %ENTIDADE%
if /i "%API%"=="graphql" if "%ENTIDADE%"=="" (
    if defined FLAG_FATURAS_GRAPHQL (
        echo FATURAS GRAPHQL: DESABILITADO
    ) else (
        echo FATURAS GRAPHQL: INCLUIDO
    )
)
echo ================================================================
echo.

REM Compilar e gerar JAR antes de executar
if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    echo Compilando projeto ^(se necessario^)...
    call "%~dp0mvn.bat" -q -DskipTests package
    if errorlevel 1 goto :COMPILE_FAIL
)

if not exist "target\extrator.jar" (
    echo ERRO: Arquivo target\extrator.jar nao encontrado!
    if /i "%PROD_MODE%"=="1" (
        echo Modo producao requer JAR precompilado.
    ) else (
        echo.
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

call :AUTH_CHECK RUN_TESTAR_API "Testar API especifica"
if errorlevel 1 exit /b 1

set "CMD_ARGS=--testar-api %API%"
if not "%ENTIDADE%"=="" set "CMD_ARGS=%CMD_ARGS% %ENTIDADE%"
if defined FLAG_FATURAS_GRAPHQL set "CMD_ARGS=%CMD_ARGS% %FLAG_FATURAS_GRAPHQL%"
echo Executando: java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" %CMD_ARGS%
echo.

java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" %CMD_ARGS%
if errorlevel 1 goto :FAIL
goto :SUCCESS

:SUCCESS
echo.
echo ================================================================
echo TESTE CONCLUIDO COM SUCESSO!
echo ================================================================
goto :END

:FAIL
echo.
echo ================================================================
echo TESTE FALHOU (Exit Code: %ERRORLEVEL%)
echo ================================================================
goto :END

:COMPILE_FAIL
echo ERRO: Compilacao falhou
echo.
pause
exit /b 1

:END
echo.
pause
exit /b 0

:CONFIGURAR_FATURAS_GRAPHQL
set "FLAG_FATURAS_GRAPHQL="
echo.
echo ================================================================
echo CONFIGURACAO DE FATURAS GRAPHQL
echo ================================================================
echo Esta etapa pode demorar bastante quando incluida.
echo.

:PERGUNTAR_FATURAS_GRAPHQL
set /p INCLUIR_FATURAS_GRAPHQL="Incluir Faturas GraphQL neste teste? (1=Sim, 2=Nao, S/N): "

if /i "%INCLUIR_FATURAS_GRAPHQL%"=="S" (
    set "FLAG_FATURAS_GRAPHQL="
    goto :RUN
)
if "%INCLUIR_FATURAS_GRAPHQL%"=="1" (
    set "FLAG_FATURAS_GRAPHQL="
    goto :RUN
)
if /i "%INCLUIR_FATURAS_GRAPHQL%"=="N" (
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    goto :RUN
)
if "%INCLUIR_FATURAS_GRAPHQL%"=="2" (
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    goto :RUN
)

echo Opcao invalida. Digite 1, 2, S ou N.
goto :PERGUNTAR_FATURAS_GRAPHQL

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
