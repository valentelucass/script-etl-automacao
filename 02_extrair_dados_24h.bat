@echo off
ECHO ==========================================================
ECHO EXTRACAO DE DADOS - ULTIMAS 24 HORAS
ECHO ==========================================================
ECHO.
ECHO Executando o script para buscar todos os dados recentes...
ECHO O dashboard sera atualizado automaticamente em instantes.
ECHO.

java -jar target/extrator-script.jar

ECHO.
ECHO Extracao concluida!
PAUSE