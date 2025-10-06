@echo off
REM ============================================================================
REM SCRIPT: Teste de API - Data Específica
REM DESCRICAO: Testa uma API específica a partir de uma data/hora específica
REM AUTOR: Sistema de Automação ESL Cloud
REM DATA: 2025
REM PARAMETROS: %1 = Tipo de API (rest, graphql, dataexport, banco)
REM             %2 = Data/hora no formato "YYYY-MM-DDTHH:mm:ss"
REM ============================================================================

ECHO ==========================================================
ECHO TESTE DE API - DATA ESPECIFICA
ECHO ==========================================================
ECHO.

REM Verificar se os parâmetros foram fornecidos
IF "%~1"=="" (
    ECHO ERRO: Faltou o nome da API.
    ECHO.
    ECHO Uso: 05_testar_api_por_data.bat [api] [data]
    ECHO APIs: rest, graphql, dataexport, banco
    ECHO Data: "YYYY-MM-DDTHH:mm:ss"
    ECHO.
    ECHO Exemplo: 05_testar_api_por_data.bat graphql "2025-10-01T00:00:00"
    ECHO.
    PAUSE
    EXIT /B 1
)

IF "%~2"=="" (
    ECHO ERRO: Faltou a data/hora entre aspas.
    ECHO.
    ECHO Uso: 05_testar_api_por_data.bat [api] [data]
    ECHO APIs: rest, graphql, dataexport, banco
    ECHO Data: "YYYY-MM-DDTHH:mm:ss"
    ECHO.
    ECHO Exemplo: 05_testar_api_por_data.bat graphql "2025-10-01T00:00:00"
    ECHO.
    PAUSE
    EXIT /B 1
)

REM Verificar se o JAR existe
IF NOT EXIST "target\extrator-script.jar" (
    ECHO ERRO: Arquivo JAR nao encontrado!
    ECHO Execute primeiro: mvn clean package
    ECHO.
    PAUSE
    EXIT /B 1
)

REM Validar tipo de API
SET "api_valida=false"
IF /I "%1"=="rest" SET "api_valida=true"
IF /I "%1"=="graphql" SET "api_valida=true"
IF /I "%1"=="dataexport" SET "api_valida=true"
IF /I "%1"=="banco" SET "api_valida=true"

IF "%api_valida%"=="false" (
    ECHO ERRO: API '%1' nao reconhecida!
    ECHO APIs validas: rest, graphql, dataexport, banco
    ECHO.
    PAUSE
    EXIT /B 1
)

REM Executar teste da API
ECHO Testando a API '%1' a partir de %2...
ECHO.

java -jar target/extrator-script.jar --teste-%1 %2

REM Verificar resultado da execução
IF %ERRORLEVEL% EQU 0 (
    ECHO.
    ECHO ==========================================================
    ECHO TESTE CONCLUIDO COM SUCESSO!
    ECHO ==========================================================
    ECHO A API '%1' esta funcionando corretamente.
) ELSE (
    ECHO.
    ECHO ==========================================================
    ECHO ERRO NO TESTE DA API!
    ECHO ==========================================================
    ECHO Codigo de erro: %ERRORLEVEL%
    ECHO Verifique os logs para mais detalhes.
)

ECHO.
ECHO Pressione qualquer tecla para continuar...
PAUSE >nul