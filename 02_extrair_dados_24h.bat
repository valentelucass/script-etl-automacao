@echo off
REM ============================================================================
REM SCRIPT: Extração de Dados - Últimas 24 Horas
REM DESCRICAO: Executa extração completa de dados das últimas 24 horas
REM AUTOR: Sistema de Automação ESL Cloud
REM DATA: 2025
REM ============================================================================

ECHO ==========================================================
ECHO EXTRACAO DE DADOS - ULTIMAS 24 HORAS
ECHO ==========================================================
ECHO.

REM Verificar se o JAR existe
IF NOT EXIST "target\extrator-script.jar" (
    ECHO ERRO: Arquivo JAR nao encontrado!
    ECHO Execute primeiro: mvn clean package
    ECHO.
    PAUSE
    EXIT /B 1
)

REM Executar extração de dados
ECHO Iniciando extracao de dados das ultimas 24 horas...
ECHO Processando APIs: REST, GraphQL e Data Export
ECHO.

java -jar target/extrator-script.jar

REM Verificar resultado da execução
IF %ERRORLEVEL% EQU 0 (
    ECHO.
    ECHO ==========================================================
    ECHO EXTRACAO CONCLUIDA COM SUCESSO!
    ECHO ==========================================================
    ECHO Os dados foram extraidos e salvos no banco de dados.
    ECHO Dashboard atualizado automaticamente.
) ELSE (
    ECHO.
    ECHO ==========================================================
    ECHO ERRO NA EXTRACAO DE DADOS!
    ECHO ==========================================================
    ECHO Codigo de erro: %ERRORLEVEL%
    ECHO Verifique os logs para mais detalhes.
)

ECHO.
ECHO Pressione qualquer tecla para continuar...
PAUSE >nul