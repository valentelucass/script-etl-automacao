@echo off
setlocal
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
if not defined MVN_CMD if defined MAVEN_HOME if exist "%MAVEN_HOME%\bin\mvn.cmd" set "MVN_CMD=%MAVEN_HOME%\bin\mvn.cmd"
if not defined MVN_CMD for /f "delims=" %%M in ('where mvn 2^>nul') do set "MVN_CMD=%%~fM"
if not defined MVN_CMD set "MVN_CMD=mvn"

if "%~1"=="" (
  echo Nenhum objetivo informado. Executando: mvn clean package -DskipTests
  if /i "%MVN_CMD%"=="mvn.cmd" (
    call mvn.cmd clean package -DskipTests
  ) else if /i "%MVN_CMD%"=="mvn" (
    call mvn clean package -DskipTests
  ) else (
    call "%MVN_CMD%" clean package -DskipTests
  )
  exit /b %ERRORLEVEL%
) else (
  if /i "%MVN_CMD%"=="mvn.cmd" (
    call mvn.cmd %*
  ) else if /i "%MVN_CMD%"=="mvn" (
    call mvn %*
  ) else (
    call "%MVN_CMD%" %*
  )
  exit /b %ERRORLEVEL%
)
