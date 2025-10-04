@echo off
ECHO ==========================================================
ECHO TESTE DE API - ULTIMAS 24 HORAS
ECHO ==========================================================
ECHO.
ECHO Uso: 05_testar_api_24h.bat [api]
ECHO APIs disponiveis: rest, graphql, dataexport, banco
ECHO.

IF "%~1"=="" (
    ECHO ERRO: Voce precisa especificar qual API testar.
    ECHO Ex: 05_testar_api_24h.bat graphql
    PAUSE
    EXIT /B 1
)

ECHO Testando a API '%1' com dados das ultimas 24 horas...
ECHO.
java -jar target/extrator-script.jar --teste-%1

ECHO.
ECHO Teste concluido!
PAUSE