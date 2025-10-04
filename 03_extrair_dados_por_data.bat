@echo off
ECHO ==========================================================
ECHO EXTRACAO DE DADOS - DATA ESPECIFICA
ECHO ==========================================================
ECHO.
ECHO Uso: arraste este arquivo para o terminal ou execute como:
ECHO 04_extrair_dados_por_data.bat "2025-10-03T15:00:00"
ECHO.

IF "%~1"=="" (
    ECHO ERRO: Voce precisa fornecer uma data/hora entre aspas.
    ECHO Exemplo: "2025-10-03T00:00:00"
    PAUSE
    EXIT /B 1
)

ECHO Buscando dados a partir de %1 ...
ECHO.
java -jar target/extrator-script.jar %1

ECHO.
ECHO Extracao concluida!
PAUSE