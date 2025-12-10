@echo off

REM ================================================================
REM Script: 07-validar-manifestos.bat
REM Finalidade:
REM   Executa validação completa de manifestos via Java:
REM   - Validação de contagem (extraídos vs salvos)
REM   - Identificação de duplicados falsos
REM   - Validação da correção do identificador único
REM   - Verificação de integridade de dados
REM
REM Uso:
REM   07-validar-manifestos.bat
REM ================================================================

echo ================================================================
echo VALIDACAO COMPLETA DE MANIFESTOS
echo ================================================================
echo.
echo Este script executa todas as validacoes de manifestos:
echo   - Validacao de contagem (extraidos vs salvos)
echo   - Identificacao de duplicados falsos
echo   - Validacao da correcao do identificador unico
echo   - Verificacao de integridade de dados
echo.
echo ================================================================
echo.

REM Configurar JAVA_HOME automaticamente
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.16.8-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

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
    echo.
    echo Certifique-se de compilar o projeto primeiro.
    echo Execute: 05-compilar_projeto.bat
    echo   ou: mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)

echo Executando validacao completa de manifestos...
echo.
echo ================================================================
echo.

java -jar target\extrator.jar --validar-manifestos

if %ERRORLEVEL% equ 0 (
    echo.
    echo ================================================================
    echo VALIDACAO CONCLUIDA COM SUCESSO
    echo ================================================================
    echo.
    echo Todas as validacoes foram executadas.
    echo Revise os resultados acima para verificar o status.
    echo.
) else (
    echo.
    echo ================================================================
    echo VALIDACAO FALHOU ^(Exit Code: %ERRORLEVEL%^)
    echo ================================================================
    echo.
    echo Possiveis causas:
    echo   1. Erro de conexao com banco de dados
    echo   2. Tabela manifestos nao existe
    echo   3. Credenciais do banco incorretas
    echo.
    echo Verifique as configuracoes e tente novamente.
    echo.
)

echo.
pause

