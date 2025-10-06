@echo off
REM ============================================================================
REM SCRIPT: Extração de Dados - Data Específica
REM DESCRICAO: Executa extração de dados a partir de uma data/hora específica
REM AUTOR: Sistema de Automação ESL Cloud
REM DATA: 2025
REM PARAMETROS: %1 = Data/hora no formato "YYYY-MM-DDTHH:mm:ss"
REM ============================================================================

ECHO ==========================================================
ECHO EXTRACAO DE DADOS - DATA ESPECIFICA
ECHO ==========================================================
ECHO.

REM Verificar se o parâmetro foi fornecido
IF "%~1"=="" (
    ECHO ERRO: Voce precisa fornecer uma data/hora entre aspas.
    ECHO.
    ECHO Uso: 03_extrair_dados_por_data.bat "YYYY-MM-DDTHH:mm:ss"
    ECHO Exemplo: 03_extrair_dados_por_data.bat "2025-10-03T15:00:00"
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

REM Executar extração de dados
ECHO Iniciando extracao de dados a partir de: %1
ECHO Processando APIs: REST, GraphQL e Data Export
ECHO.

java -jar target/extrator-script.jar %1

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