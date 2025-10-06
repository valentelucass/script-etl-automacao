@echo off
REM ============================================================================
REM SCRIPT: Iniciar Dashboard Completo
REM DESCRICAO: Compila o projeto Maven e inicia backend + frontend
REM AUTOR: Sistema de Automação ESL Cloud
REM DATA: 2025
REM ============================================================================

ECHO ==========================================================
ECHO INICIANDO DASHBOARD COMPLETO - ESL CLOUD
ECHO ==========================================================
ECHO.

REM Compilar o projeto Maven
ECHO [ETAPA 1/3] Compilando o projeto Maven...
mvn clean package -DskipTests

REM Verificar se a compilação foi bem-sucedida
IF %ERRORLEVEL% NEQ 0 (
    ECHO.
    ECHO ERRO: Falha na compilacao do projeto Maven!
    ECHO Verifique os logs acima para mais detalhes.
    PAUSE
    EXIT /B 1
)

REM Iniciar o backend Java em uma nova janela
ECHO.
ECHO [ETAPA 2/3] Iniciando o servidor backend...
START "Servidor Backend" java -jar target/extrator-esl-cloud-1.0-SNAPSHOT-dashboard.jar

REM Aguardar um momento para o backend inicializar
TIMEOUT /T 3 /NOBREAK >nul

REM Iniciar o frontend React em uma nova janela
ECHO.
ECHO [ETAPA 3/3] Iniciando o frontend React...
start "Frontend React" cmd /k "cd dashboard-monitoramento && npm start"

ECHO.
ECHO ==========================================================
ECHO DASHBOARD INICIADO COM SUCESSO!
ECHO ==========================================================
ECHO Backend: Executando em segundo plano
ECHO Frontend: Abrindo em nova janela
ECHO.
ECHO Pressione qualquer tecla para fechar este script...
PAUSE >nul