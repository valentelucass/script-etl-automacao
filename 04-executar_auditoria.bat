@echo off
setlocal

REM ================================================================
REM Script: 04-executar_auditoria.bat
REM Finalidade:
REM   Executa auditoria dos dados extraidos nas ultimas 24 horas.
REM   Util para verificar a integridade dos dados.
REM
REM Uso:
REM   04-executar_auditoria.bat
REM
REM Funcionalidades:
REM   - Verifica dados das ultimas 24h
REM   - Identifica inconsistencias
REM   - Gera relatorio de auditoria
REM ================================================================

echo ================================================================
echo EXECUTANDO AUDITORIA DOS DADOS
echo ================================================================

echo Compilando projeto...
call "%~dp0mvn.bat" -q -DskipTests package
if errorlevel 1 (
    echo ERRO: Compilacao falhou
    echo.
    pause
    exit /b 1
)

if not exist "target\extrator.jar" (
    echo ERRO: Arquivo target\extrator.jar nao encontrado!
    echo Execute primeiro: mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)

echo Executando: java -jar "target\extrator.jar" --auditoria
echo.
echo Verificando dados das ultimas 24 horas...
echo.

java -jar "target\extrator.jar" --auditoria

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================================================
    echo AUDITORIA CONCLUIDA COM SUCESSO!
    echo ================================================================
) else (
    echo.
    echo ================================================================
    echo AUDITORIA FALHOU ^(Exit Code: %ERRORLEVEL%^)
    echo ================================================================
)

echo.
pause
