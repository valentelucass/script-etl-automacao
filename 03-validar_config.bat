@echo off
setlocal

REM ================================================================
REM Script: 03-validar_configuracoes.bat
REM Finalidade:
REM   Valida as configuracoes do sistema e conectividade com APIs.
REM   Util para diagnosticar problemas antes da extracao.
REM
REM Uso:
REM   03-validar_configuracoes.bat
REM
REM Funcionalidades:
REM   - Testa conectividade com APIs
REM   - Valida configuracoes de banco
REM   - Verifica credenciais
REM ================================================================

echo ================================================================
echo VALIDANDO CONFIGURACOES DO SISTEMA
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

echo Executando: java -jar "target\extrator.jar" --validar
echo.

java -jar "target\extrator.jar" --validar

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================================================
    echo VALIDACAO CONCLUIDA COM SUCESSO!
    echo ================================================================
) else (
    echo.
    echo ================================================================
    echo VALIDACAO FALHOU ^(Exit Code: %ERRORLEVEL%^)
    echo ================================================================
)

echo.
pause
