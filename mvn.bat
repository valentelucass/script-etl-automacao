@echo off
setlocal EnableExtensions EnableDelayedExpansion
REM ==[DOC-FILE]===============================================================
REM Arquivo : mvn.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "mvn" para uso operacional.
REM
REM Conecta com:
REM - mvn (build/test/execucao Java)
REM - call: :run_maven
REM - call: mvn.cmd
REM - call: mvn
REM - call: %MVN_CMD%
REM
REM Fluxo geral:
REM 1) Inicializa variaveis de controle do script.
REM 2) Executa rotina principal com validacoes basicas.
REM 3) Finaliza com codigo de retorno apropriado.
REM
REM Variaveis-chave:
REM - __JAVA_BIN: controle de estado do script.
REM - MVN_CMD: controle de estado do script.
REM - __ARGS: controle de estado do script.
REM - __REMOVED_CLEAN: controle de estado do script.
REM [DOC-FILE-END]===========================================================

chcp 1252 >nul

if not defined JAVA_HOME (
  for /f "delims=" %%J in ('where java 2^>nul') do (
    set "__JAVA_BIN=%%~dpJ"
    goto :setjava
  )
  :setjava
  if defined __JAVA_BIN (
    for %%A in ("%__JAVA_BIN%..") do set "JAVA_HOME=%%~fA"
    if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
  )
  if not exist "%JAVA_HOME%\bin\java.exe" (
    for /f "delims=" %%D in ('dir /b /ad "C:\\Program Files\\Java\\jdk*" 2^>nul') do set "JAVA_HOME=C:\\Program Files\\Java\\%%D"
    if not exist "%JAVA_HOME%\bin\java.exe" for /f "delims=" %%D in ('dir /b /ad "C:\\Program Files\\Eclipse Adoptium\\jdk*" 2^>nul') do set "JAVA_HOME=C:\\Program Files\\Eclipse Adoptium\\%%D"
    if exist "%JAVA_HOME%\bin\java.exe" set "PATH=%JAVA_HOME%\bin;%PATH%"
  )
)

set "MVN_CMD="
for /f "delims=" %%M in ('where mvn.cmd 2^>nul') do (
  if /i not "%%~fM"=="%~f0" (
    set "MVN_CMD=%%~fM"
    goto :foundmvn
  )
)
:foundmvn
if not defined MVN_CMD if defined M2_HOME if exist "%M2_HOME%\bin\mvn.cmd" set "MVN_CMD=%M2_HOME%\bin\mvn.cmd"
if not defined MVN_CMD for /f "delims=" %%M in ('where mvn 2^>nul') do set "MVN_CMD=%%~fM"
if not defined MVN_CMD set "MVN_CMD=mvn"

if "%~1"=="" (
  echo Nenhum objetivo informado. Executando: mvn package -DskipTests
  call :run_maven package -DskipTests
  exit /b !ERRORLEVEL!
) else (
  set "__ARGS="
  set "__REMOVED_CLEAN=0"
  :parse_args
  if "%~1"=="" goto :parsed_args
  if /i "%~1"=="clean" (
    set "__REMOVED_CLEAN=1"
  ) else (
    if defined __ARGS (
      set "__ARGS=!__ARGS! %1"
    ) else (
      set "__ARGS=%1"
    )
  )
  shift
  goto :parse_args

  :parsed_args
  if "!__REMOVED_CLEAN!"=="1" (
    echo Aviso: objetivo 'clean' removido automaticamente para evitar locks em target no Windows/OneDrive.
  )
  if not defined __ARGS (
    echo Nenhum objetivo restante. Executando: mvn package -DskipTests
    call :run_maven package -DskipTests
    exit /b !ERRORLEVEL!
  )
  call :run_maven !__ARGS!
  exit /b !ERRORLEVEL!
)

:run_maven
if /i "%MVN_CMD%"=="mvn.cmd" (
  call mvn.cmd %*
) else if /i "%MVN_CMD%"=="mvn" (
  call mvn %*
) else (
  call "%MVN_CMD%" %*
)
exit /b %ERRORLEVEL%
