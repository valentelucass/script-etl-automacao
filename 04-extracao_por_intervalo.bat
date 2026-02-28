@echo off
setlocal EnableDelayedExpansion
REM ==[DOC-FILE]===============================================================
REM Arquivo : 04-extracao_por_intervalo.bat
REM Tipo    : Script operacional Windows (.bat)
REM Papel   : Automatiza a rotina "04-extracao por intervalo" para uso operacional.
REM
REM Conecta com:
REM - java -jar: target\extrator.jar
REM - call: :VALIDAR_DATA
REM - call: :CONFIGURAR_FATURAS_GRAPHQL
REM - call: %~dp0mvn.bat
REM - mvn (build/test/execucao Java)
REM
REM Fluxo geral:
REM 1) Prepara parametros de periodo e escopo de extracao.
REM 2) Executa o jar com comando alvo.
REM 3) Consolida log e retorno da rodada.
REM
REM Variaveis-chave:
REM - FINAL_EXIT_CODE: controle de estado do script.
REM - FLAG_FATURAS_GRAPHQL: controle de estado do script.
REM - PARAM_FLAG_FATURAS: controle de estado do script.
REM - DATA_INICIO: controle de estado do script.
REM - DATA_FIM: controle de estado do script.
REM - API_ESCOLHIDA: controle de estado do script.
REM - ENTIDADE_ESCOLHIDA: controle de estado do script.
REM - CMD_ARGS: controle de estado do script.
REM - JAVA_EXIT_CODE: controle de estado do script.
REM - RET_CODE: controle de estado do script.
REM - DATA_TESTE: controle de estado do script.
REM - DATA_NUMERICA: controle de estado do script.
REM [DOC-FILE-END]===========================================================

if /i not "%EXTRATOR_SKIP_CHCP%"=="1" chcp 65001 >nul
set "FINAL_EXIT_CODE=0"
set "FLAG_FATURAS_GRAPHQL="
set "PARAM_FLAG_FATURAS="

REM ================================================================
REM Script: 04-extracao_por_intervalo.bat
REM Finalidade:
REM   Executa a extracao de dados por intervalo de datas.
REM   Permite extrair dados de um periodo especifico (ex: 2024-11-01 a 2025-03-31).
REM   O sistema divide automaticamente em blocos de 30 dias e valida
REM   regras de limitacao de tempo (cada bloco de 30 dias = sem limite de horas).
REM   Permite escolher API e entidade especifica.
REM
REM Uso:
REM   04-extracao_por_intervalo.bat
REM   04-extracao_por_intervalo.bat YYYY-MM-DD YYYY-MM-DD
REM   04-extracao_por_intervalo.bat YYYY-MM-DD YYYY-MM-DD api
REM   04-extracao_por_intervalo.bat YYYY-MM-DD YYYY-MM-DD api entidade
REM   04-extracao_por_intervalo.bat YYYY-MM-DD YYYY-MM-DD [api] [entidade] --sem-faturas-graphql
REM   04-extracao_por_intervalo.bat YYYY-MM-DD YYYY-MM-DD [api] [entidade] --com-faturas-graphql
REM
REM Exemplos:
REM   04-extracao_por_intervalo.bat 2024-10-26 2024-12-26
REM   04-extracao_por_intervalo.bat 2024-10-26 2024-12-26 dataexport
REM   04-extracao_por_intervalo.bat 2024-10-26 2024-12-26 dataexport localizacao_cargas
REM   04-extracao_por_intervalo.bat 2024-10-26 2024-12-26 --sem-faturas-graphql
REM   04-extracao_por_intervalo.bat 2024-10-26 2024-12-26 --com-faturas-graphql
REM
REM Funcionalidades:
REM   - Aceita parametros na linha de comando OU menu interativo
REM   - Permite escolher API especifica (GraphQL ou DataExport)
REM   - Permite escolher entidade especifica
REM   - Divide periodo em blocos de 30 dias automaticamente
REM   - Cada bloco de 30 dias e tratado como "< 31 dias" (sem limite de horas)
REM   - Executa extracao para cada bloco sequencialmente
REM   - Gera logs detalhados
REM ================================================================

if /i not "%EXTRATOR_SKIP_AUTH_CHECK%"=="1" (
    if not exist "target\extrator.jar" (
        echo ERRO: Arquivo target\extrator.jar nao encontrado para autenticacao.
        echo.
        pause
        exit /b 1
    )
    echo.
    echo Autenticacao obrigatoria para executar esta acao.
    java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --auth-check RUN_EXTRACAO_INTERVALO "Executar extracao por intervalo"
    if errorlevel 1 (
        echo Acesso negado.
        echo.
        pause
        exit /b 1
    )
)

REM Verificar se parametros foram passados na linha de comando
if "%~1"=="" (
    REM Modo interativo - sem parametros
    goto :MODO_INTERATIVO
)

REM Modo com parametros - usar valores da linha de comando
set "DATA_INICIO=%~1"
set "DATA_FIM=%~2"
set "API_ESCOLHIDA=%~3"
set "ENTIDADE_ESCOLHIDA=%~4"
set "PARAM_FLAG_FATURAS=%~5"

if /i "%API_ESCOLHIDA%"=="--sem-faturas-graphql" (
    set "API_ESCOLHIDA="
    set "PARAM_FLAG_FATURAS=--sem-faturas-graphql"
)
if /i "%API_ESCOLHIDA%"=="--com-faturas-graphql" (
    set "API_ESCOLHIDA="
    set "PARAM_FLAG_FATURAS=--com-faturas-graphql"
)
if /i "%ENTIDADE_ESCOLHIDA%"=="--sem-faturas-graphql" (
    set "ENTIDADE_ESCOLHIDA="
    set "PARAM_FLAG_FATURAS=--sem-faturas-graphql"
)
if /i "%ENTIDADE_ESCOLHIDA%"=="--com-faturas-graphql" (
    set "ENTIDADE_ESCOLHIDA="
    set "PARAM_FLAG_FATURAS=--com-faturas-graphql"
)

REM Validar que pelo menos as datas foram fornecidas
if "%DATA_INICIO%"=="" (
    echo ERRO: Data de inicio nao informada!
    echo.
    echo Uso: 04-extracao_por_intervalo.bat [DATA_INICIO] [DATA_FIM] [API] [ENTIDADE] [--sem-faturas-graphql^|--com-faturas-graphql]
    echo Exemplo: 04-extracao_por_intervalo.bat 2024-10-26 2024-12-26 dataexport localizacao_cargas
    pause
    exit /b 1
)

if "%DATA_FIM%"=="" (
    echo ERRO: Data de fim nao informada!
    echo.
    echo Uso: 04-extracao_por_intervalo.bat [DATA_INICIO] [DATA_FIM] [API] [ENTIDADE] [--sem-faturas-graphql^|--com-faturas-graphql]
    echo Exemplo: 04-extracao_por_intervalo.bat 2024-10-26 2024-12-26 dataexport localizacao_cargas
    pause
    exit /b 1
)

REM Validar formato basico das datas (YYYY-MM-DD)
REM Validacao completa (data real) sera feita pelo Java ao parsear

call :VALIDAR_DATA "%DATA_INICIO%"
if errorlevel 1 (
    echo ERRO: Data de inicio deve estar no formato YYYY-MM-DD
    echo Valor recebido: %DATA_INICIO%
    pause
    exit /b 1
)

call :VALIDAR_DATA "%DATA_FIM%"
if errorlevel 1 (
    echo ERRO: Data de fim deve estar no formato YYYY-MM-DD
    echo Valor recebido: %DATA_FIM%
    pause
    exit /b 1
)

REM Vari??veis j?? est??o definidas, continuar

REM Pular para confirmacao
goto :CONFIRMACAO

:MODO_INTERATIVO
echo ================================================================
echo EXTRACAO POR INTERVALO DE DATAS
echo ================================================================
echo.
echo Este script permite extrair dados de um periodo especifico.
echo O sistema dividira automaticamente em blocos de 31 dias.
echo.
echo Formato de data: YYYY-MM-DD ^(exemplo: 2024-11-01^)
echo.

REM Solicitar data de inicio
set /p DATA_INICIO="Digite a data de inicio (YYYY-MM-DD): "
if "%DATA_INICIO%"=="" (
    echo ERRO: Data de inicio nao informada!
    pause
    exit /b 1
)

REM Limpar espacos acidentais e validar formato basico
set "DATA_INICIO=%DATA_INICIO: =%"
call :VALIDAR_DATA "%DATA_INICIO%"
if errorlevel 1 (
    echo ERRO: Formato de data invalido! Use YYYY-MM-DD ^(exemplo: 2024-11-01^)
    pause
    exit /b 1
)

REM Solicitar data de fim
set /p DATA_FIM="Digite a data de fim (YYYY-MM-DD): "
if "%DATA_FIM%"=="" (
    echo ERRO: Data de fim nao informada!
    pause
    exit /b 1
)

REM Limpar espacos acidentais e validar formato basico
set "DATA_FIM=%DATA_FIM: =%"
call :VALIDAR_DATA "%DATA_FIM%"
if errorlevel 1 (
    echo ERRO: Formato de data invalido! Use YYYY-MM-DD ^(exemplo: 2025-03-31^)
    pause
    exit /b 1
)

echo.
echo ================================================================
echo   ESCOLHA DE API E ENTIDADE
echo ================================================================
echo.
echo Escolha uma opcao:
echo.
echo   1. Extrair TODAS as APIs e entidades
echo   2. Extrair API especifica ^(GraphQL ou DataExport^)
echo   0. Cancelar
echo.
set /p OPCAO_API="Digite o numero da opcao: "

if "%OPCAO_API%"=="0" (
    echo Operacao cancelada.
    exit /b 0
)

set "API_ESCOLHIDA="
set "ENTIDADE_ESCOLHIDA="

if "%OPCAO_API%"=="2" (
    echo.
    echo ================================================================
    echo   ESCOLHA DA API
    echo ================================================================
    echo.
    echo   1. GraphQL ^(Coletas, Fretes, Faturas GraphQL^)
    echo   2. DataExport ^(Manifestos, Cotacoes, Localizacao de Cargas, Contas a Pagar, Faturas por Cliente^)
    echo   0. Voltar
    echo.
    set /p API_NUM="Digite o numero da API: "
    
    if "!API_NUM!"=="0" (
        echo Voltando...
        goto :END
    )
    
    if "!API_NUM!"=="1" (
        set "API_ESCOLHIDA=graphql"
    ) else if "!API_NUM!"=="2" (
        set "API_ESCOLHIDA=dataexport"
    ) else (
        echo ERRO: Opcao invalida!
        pause
        exit /b 1
    )
    
    echo.
    echo ================================================================
    echo   ESCOLHA DA ENTIDADE
    echo ================================================================
    echo.
    echo   0. Extrair TODAS as entidades da API escolhida
    echo.
    
    if "!API_ESCOLHIDA!"=="graphql" (
        echo   Entidades GraphQL:
        echo   1. coletas
        echo   2. fretes
        echo   3. faturas_graphql
        echo   4. usuarios_sistema
        echo.
        set /p ENTIDADE_NUM="Digite o numero da entidade (0 = todas): "
        
        if "!ENTIDADE_NUM!"=="0" (
            set "ENTIDADE_ESCOLHIDA="
        ) else if "!ENTIDADE_NUM!"=="1" (
            set "ENTIDADE_ESCOLHIDA=coletas"
        ) else if "!ENTIDADE_NUM!"=="2" (
            set "ENTIDADE_ESCOLHIDA=fretes"
        ) else if "!ENTIDADE_NUM!"=="3" (
            set "ENTIDADE_ESCOLHIDA=faturas_graphql"
        ) else if "!ENTIDADE_NUM!"=="4" (
            set "ENTIDADE_ESCOLHIDA=usuarios_sistema"
        ) else (
            echo ERRO: Numero invalido!
            pause
            exit /b 1
        )
    ) else if "!API_ESCOLHIDA!"=="dataexport" (
        echo   Entidades DataExport:
        echo   1. manifestos
        echo   2. cotacoes
        echo   3. localizacao_cargas
        echo   4. contas_a_pagar
        echo   5. faturas_por_cliente
        echo.
        set /p ENTIDADE_NUM="Digite o numero da entidade (0 = todas): "
        
        if "!ENTIDADE_NUM!"=="0" (
            set "ENTIDADE_ESCOLHIDA="
        ) else if "!ENTIDADE_NUM!"=="1" (
            set "ENTIDADE_ESCOLHIDA=manifestos"
        ) else if "!ENTIDADE_NUM!"=="2" (
            set "ENTIDADE_ESCOLHIDA=cotacoes"
        ) else if "!ENTIDADE_NUM!"=="3" (
            set "ENTIDADE_ESCOLHIDA=localizacao_cargas"
        ) else if "!ENTIDADE_NUM!"=="4" (
            set "ENTIDADE_ESCOLHIDA=contas_a_pagar"
        ) else if "!ENTIDADE_NUM!"=="5" (
            set "ENTIDADE_ESCOLHIDA=faturas_por_cliente"
        ) else (
            echo ERRO: Numero invalido!
            pause
            exit /b 1
        )
    )
)

:CONFIRMACAO
call :CONFIGURAR_FATURAS_GRAPHQL
if errorlevel 1 (
    echo ERRO ao configurar opcao de Faturas GraphQL.
    pause
    exit /b 1
)

echo.
echo ================================================================
echo Confirmacao
echo ================================================================
echo Data de Inicio: %DATA_INICIO%
echo Data de Fim: %DATA_FIM%
if not "%API_ESCOLHIDA%"=="" (
    echo API: %API_ESCOLHIDA%
    if not "%ENTIDADE_ESCOLHIDA%"=="" (
        echo Entidade: %ENTIDADE_ESCOLHIDA%
    ) else (
        echo Entidade: TODAS
    )
) else (
    echo API: TODAS
    echo Entidade: TODAS
)
if defined FLAG_FATURAS_GRAPHQL (
    echo Faturas GraphQL: DESABILITADO
) else (
    echo Faturas GraphQL: INCLUIDO
)
echo.

REM Se parametros foram passados, pular confirmacao interativa
if not "%~1"=="" (
    echo Parametros recebidos via linha de comando. Iniciando extracao...
    goto :COMPILAR
)

:PERGUNTAR_CONFIRMACAO
set /p CONFIRMA="Confirma a extracao para este periodo? (1=Sim, 2=Nao, S/N): "
if /i "%CONFIRMA%"=="S" goto :COMPILAR
if "%CONFIRMA%"=="1" goto :COMPILAR
if /i "%CONFIRMA%"=="N" (
    echo Operacao cancelada pelo usuario.
    pause
    exit /b 0
)
if "%CONFIRMA%"=="2" (
    echo Operacao cancelada pelo usuario.
    pause
    exit /b 0
)
echo Opcao invalida. Digite 1, 2, S ou N.
goto :PERGUNTAR_CONFIRMACAO

:COMPILAR
echo.
echo ================================================================
echo Compilando projeto...
echo ================================================================

if /i "%PROD_MODE%"=="1" (
    echo Modo producao: pulando compilacao.
) else (
    call "%~dp0mvn.bat" -DskipTests package
    if errorlevel 1 (
        echo ERRO: Compilacao falhou
        echo.
        pause
        exit /b 1
    )
)

if not exist "target\extrator.jar" (
    echo ERRO: Arquivo target\extrator.jar nao encontrado!
    if /i "%PROD_MODE%"=="1" (
        echo Modo producao requer JAR precompilado.
    ) else (
        echo Execute primeiro: mvn package -DskipTests
    )
    echo.
    pause
    exit /b 1
)

echo.
echo ================================================================
echo Iniciando extracao por intervalo
echo ================================================================
echo Periodo: %DATA_INICIO% a %DATA_FIM%
if not "%API_ESCOLHIDA%"=="" (
    echo API: %API_ESCOLHIDA%
    if not "%ENTIDADE_ESCOLHIDA%"=="" (
        echo Entidade: %ENTIDADE_ESCOLHIDA%
    ) else (
        echo Entidade: TODAS
    )
) else (
    echo API: TODAS
    echo Entidade: TODAS
)
if defined FLAG_FATURAS_GRAPHQL (
    echo Faturas GraphQL: DESABILITADO
) else (
    echo Faturas GraphQL: INCLUIDO
)
echo.
echo ATENCAO: Este processo pode demorar varios minutos...
echo O sistema dividira automaticamente em blocos de 30 dias ^(sem limite de horas^).
echo.
echo.

REM Construir comando com par??metros opcionais usando delayed expansion
set "CMD_ARGS=!DATA_INICIO! !DATA_FIM!"
if not "!API_ESCOLHIDA!"=="" (
    set "CMD_ARGS=!CMD_ARGS! !API_ESCOLHIDA!"
    if not "!ENTIDADE_ESCOLHIDA!"=="" (
        set "CMD_ARGS=!CMD_ARGS! !ENTIDADE_ESCOLHIDA!"
    )
)
if defined FLAG_FATURAS_GRAPHQL (
    set "CMD_ARGS=!CMD_ARGS! !FLAG_FATURAS_GRAPHQL!"
)

REM Executar comando
java --enable-native-access=ALL-UNNAMED -jar "target\extrator.jar" --extracao-intervalo !CMD_ARGS!
set "JAVA_EXIT_CODE=%ERRORLEVEL%"
set "FINAL_EXIT_CODE=%JAVA_EXIT_CODE%"

if "%JAVA_EXIT_CODE%"=="0" (
    echo.
    echo ================================================================
    echo EXTRACAO POR INTERVALO CONCLUIDA COM SUCESSO!
    echo ================================================================
) else if "%JAVA_EXIT_CODE%"=="2" (
    echo.
    echo ================================================================
    echo EXTRACAO POR INTERVALO CONCLUIDA COM FALHAS PARCIAIS ^(Exit Code: %JAVA_EXIT_CODE%^)
    echo ================================================================
) else (
    echo.
    echo ================================================================
    echo EXTRACAO FALHOU ^(Exit Code: %JAVA_EXIT_CODE%^)
    echo ================================================================
)

:END
echo.
echo Verifique os logs na pasta 'logs' para mais detalhes.
echo.
pause
set "RET_CODE=%FINAL_EXIT_CODE%"
endlocal & exit /b %RET_CODE%

:CONFIGURAR_FATURAS_GRAPHQL
set "FLAG_FATURAS_GRAPHQL="
set /a FATURAS_TENTATIVAS=0

if /i "%ENTIDADE_ESCOLHIDA%"=="faturas_graphql" (
    echo.
    echo Faturas GraphQL: INCLUIDO automaticamente ^(entidade escolhida explicitamente^).
    exit /b 0
)
if /i "%ENTIDADE_ESCOLHIDA%"=="faturas" (
    echo.
    echo Faturas GraphQL: INCLUIDO automaticamente ^(entidade escolhida explicitamente^).
    exit /b 0
)
if /i "%ENTIDADE_ESCOLHIDA%"=="faturasgraphql" (
    echo.
    echo Faturas GraphQL: INCLUIDO automaticamente ^(entidade escolhida explicitamente^).
    exit /b 0
)

if /i "%API_ESCOLHIDA%"=="dataexport" (
    echo.
    echo Faturas GraphQL: nao se aplica ^(API DataExport selecionada^).
    exit /b 0
)

if /i "%PARAM_FLAG_FATURAS%"=="--sem-faturas-graphql" (
    echo.
    echo Faturas GraphQL: DESABILITADO por parametro informado.
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    exit /b 0
)
if /i "%PARAM_FLAG_FATURAS%"=="--com-faturas-graphql" (
    echo.
    echo Faturas GraphQL: INCLUIDO por parametro informado.
    set "FLAG_FATURAS_GRAPHQL="
    exit /b 0
)

echo.
echo ================================================================
echo CONFIGURACAO DE FATURAS GRAPHQL
echo ================================================================
echo Esta entidade usa enriquecimento e pode aumentar bastante o tempo.
echo.

:PERGUNTAR_FATURAS_GRAPHQL
set /a FATURAS_TENTATIVAS+=1
set /p INCLUIR_FATURAS_GRAPHQL="Incluir Faturas GraphQL nesta extracao? (1=Sim, 2=Nao, S/N): "
if /i "!INCLUIR_FATURAS_GRAPHQL!"=="S" (
    set "FLAG_FATURAS_GRAPHQL="
    echo Faturas GraphQL: INCLUIDO.
    exit /b 0
)
if "!INCLUIR_FATURAS_GRAPHQL!"=="1" (
    set "FLAG_FATURAS_GRAPHQL="
    echo Faturas GraphQL: INCLUIDO.
    exit /b 0
)
if /i "!INCLUIR_FATURAS_GRAPHQL!"=="N" (
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    echo Faturas GraphQL: DESABILITADO.
    exit /b 0
)
if "!INCLUIR_FATURAS_GRAPHQL!"=="2" (
    set "FLAG_FATURAS_GRAPHQL=--sem-faturas-graphql"
    echo Faturas GraphQL: DESABILITADO.
    exit /b 0
)
echo Opcao invalida. Digite 1, 2, S ou N.
if !FATURAS_TENTATIVAS! GEQ 10 (
    echo ERRO: Numero maximo de tentativas atingido ao configurar Faturas GraphQL.
    exit /b 1
)
goto :PERGUNTAR_FATURAS_GRAPHQL

:VALIDAR_DATA
set "DATA_TESTE=%~1"
if "%DATA_TESTE%"=="" exit /b 1
if not "%DATA_TESTE:~4,1%"=="-" exit /b 1
if not "%DATA_TESTE:~7,1%"=="-" exit /b 1
if not "%DATA_TESTE:~10,1%"=="" exit /b 1

set "DATA_NUMERICA=%DATA_TESTE:-=%"
if not "%DATA_NUMERICA:~8,1%"=="" exit /b 1
for /f "delims=0123456789" %%A in ("%DATA_NUMERICA%") do exit /b 1
exit /b 0
