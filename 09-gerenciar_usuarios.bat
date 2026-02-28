@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM ==[DOC-FILE]===============================================================
REM Arquivo : 09-gerenciar_usuarios.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "09-gerenciar usuarios" para uso operacional.
REM
REM Conecta com:
REM - call: %~dp0mvn.bat
REM - java -jar: %~dp0target\extrator.jar
REM
REM Fluxo geral:
REM 1) Inicializa variaveis de controle do script.
REM 2) Executa rotina principal com validacoes basicas.
REM 3) Finaliza com codigo de retorno apropriado.
REM
REM Variaveis-chave:
REM - JAVA_HOME: controle de estado do script.
REM - OP: controle de estado do script.
REM [DOC-FILE-END]===========================================================

if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 65001 >nul

echo ================================================================
echo GERENCIAMENTO DE USUARIOS DE ACESSO
echo ================================================================
echo.

if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    echo Compilando projeto...
    call "%~dp0mvn.bat" -q -DskipTests package
    if errorlevel 1 (
        echo ERRO: Compilacao falhou
        echo.
        pause
        exit /b 1
    )
)

if not exist "%~dp0target\extrator.jar" (
    echo ERRO: Arquivo target\extrator.jar nao encontrado!
    if /i "%PROD_MODE%"=="1" (
        echo Modo producao requer JAR precompilado.
    )
    echo.
    pause
    exit /b 1
)

if not defined JAVA_HOME (
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-17*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
    for /f "delims=" %%D in ('dir /b /ad /o-n "C:\Program Files\Eclipse Adoptium\jdk-*" 2^>nul') do (
        set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\%%D"
        goto :javahomefound
    )
)
:javahomefound
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "PATH=%JAVA_HOME%\bin;%PATH%"
    )
)

:MENU
echo.
echo ================================================================
echo MENU DE SEGURANCA
echo ================================================================
echo  1. Criar primeiro ADMIN (bootstrap)
echo  2. Criar usuario
echo  3. Redefinir senha de usuario
echo  4. Desativar usuario
echo  5. Exibir informacoes do banco de seguranca
echo  0. Voltar
echo.
set "OP="
set /p "OP=Escolha uma opcao: " || (
    echo Entrada encerrada. Encerrando menu de usuarios.
    goto :END
)
set "OP=%OP: =%"
echo.

if "%OP%"=="0" goto :END

if "%OP%"=="1" (
    echo.
    java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --auth-bootstrap
    echo.
    pause
    goto :MENU
)

if "%OP%"=="2" (
    echo.
    java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --auth-create-user
    echo.
    pause
    goto :MENU
)

if "%OP%"=="3" (
    echo.
    java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --auth-reset-password
    echo.
    pause
    goto :MENU
)

if "%OP%"=="4" (
    echo.
    java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --auth-disable-user
    echo.
    pause
    goto :MENU
)

if "%OP%"=="5" (
    echo.
    java --enable-native-access=ALL-UNNAMED -jar "%~dp0target\extrator.jar" --auth-info
    echo.
    pause
    goto :MENU
)

echo Opcao invalida.
timeout /t 2 /nobreak >nul 2>&1
goto :MENU

:END
endlocal
exit /b 0
