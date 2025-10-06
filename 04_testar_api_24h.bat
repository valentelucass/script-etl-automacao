@echo off
REM ============================================================================
REM SCRIPT: Teste de API - Últimas 24 Horas
REM DESCRICAO: Testa uma API específica com dados das últimas 24 horas
REM AUTOR: Sistema de Automação ESL Cloud
REM DATA: 2025
REM PARAMETROS: %1 = Tipo de API (rest, graphql, dataexport, banco)
REM ============================================================================

ECHO ==========================================================
ECHO TESTE DE API - ULTIMAS 24 HORAS
ECHO ==========================================================
ECHO.

REM Verificar se o parâmetro foi fornecido
IF "%~1"=="" (
    ECHO ERRO: Voce precisa especificar qual API testar.
    ECHO.
    ECHO Uso: 04_testar_api_24h.bat [api]
    ECHO APIs disponiveis: rest, graphql, dataexport, banco
    ECHO.
    ECHO Exemplo: 04_testar_api_24h.bat graphql
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
IF /I "%~1"=="rest" SET "api_valida=true"
IF /I "%~1"=="graphql" SET "api_valida=true"
IF /I "%~1"=="dataexport" SET "api_valida=true"
IF /I "%~1"=="banco" SET "api_valida=true"

IF "%api_valida%"=="false" (
    ECHO ERRO: API '%1' nao reconhecida!
    ECHO APIs validas: rest, graphql, dataexport, banco
    ECHO.
    PAUSE
    EXIT /B 1
)

REM Executar teste da API
ECHO Testando a API '%1' com dados das ultimas 24 horas...
ECHO.

java -jar target/extrator-script.jar --teste-%1

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