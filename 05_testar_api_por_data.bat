@echo off
ECHO ==========================================================
ECHO TESTE DE API - DATA ESPECIFICA
ECHO ==========================================================
ECHO.
ECHO Uso: 06_testar_api_por_data.bat [api] [data]
ECHO APIs: rest, graphql, dataexport, banco
ECHO Data: "2025-10-03T15:00:00"
ECHO Exemplo: 06_testar_api_por_data.bat graphql "2025-10-01T00:00:00"
ECHO.

IF "%~1"=="" (
    ECHO ERRO: Faltou o nome da API.
    PAUSE
    EXIT /B 1
)

IF "%~2"=="" (
    ECHO ERRO: Faltou a data/hora entre aspas.
    PAUSE
    EXIT /B 1
)

ECHO Testando a API '%1' a partir de %2 ...
ECHO.
java -jar target/extrator-script.jar --teste-%1 %2

ECHO.
ECHO Teste concluido!
PAUSE