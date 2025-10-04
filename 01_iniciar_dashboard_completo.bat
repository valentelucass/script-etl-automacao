@echo off
TITLE INICIADOR DO DASHBOARD COMPLETO

ECHO ==========================================================
ECHO INICIANDO AMBIENTE COMPLETO DO DASHBOARD
ECHO ==========================================================
ECHO.
ECHO [ETAPA 1 de 2] Compilando a versao mais recente do projeto...
ECHO.
call mvn clean package -DskipTests

IF %ERRORLEVEL% NEQ 0 (
    ECHO ERRO: Falha na compilacao com Maven. Verifique o pom.xml e as dependencias.
    PAUSE
    EXIT /B 1
)

ECHO.
ECHO Compilacao concluida com sucesso!
ECHO.
ECHO [ETAPA 2 de 2] Iniciando Backend e Frontend em janelas separadas...
ECHO.

ECHO Iniciando Servidor Backend (Java)...
START "Servidor Backend" java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar

ECHO Iniciando Interface Frontend (React)...
START "Dashboard Frontend" cmd /c "cd dashboard-monitoramento && npm start"

ECHO.
ECHO Ambiente iniciado! Duas novas janelas foram abertas.
ECHO Voce pode fechar esta janela agora.
ECHO Para parar o sistema, feche as duas novas janelas abertas.
ECHO.