param(
    [string] $RepoRoot
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($RepoRoot)) {
    $scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
    $RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $scriptDir '..\..'))
}

$RepoRoot = [System.IO.Path]::GetFullPath($RepoRoot)
$JarPath = Join-Path $RepoRoot 'target\extrator.jar'
$LogDir = Join-Path $RepoRoot 'logs\expurgo'
$FailureMarker = Join-Path $LogDir 'ultimo_erro_expurgo_orfaos.txt'
$RunStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$LogPath = Join-Path $LogDir ("expurgo-orfaos-$RunStamp.log")
$EventSource = 'ETL-Expurgo-Orfaos'

function Write-LogLine {
    param([string] $Message)
    $line = "[{0}] {1}" -f (Get-Date -Format o), $Message
    $line | Tee-Object -FilePath $LogPath -Append
}

function Import-OperationalEnvironment {
    $keys = @(
        'DB_URL',
        'DB_USER',
        'DB_PASSWORD',
        'API_BASEURL',
        'API_REST_TOKEN',
        'API_GRAPHQL_TOKEN',
        'API_DATAEXPORT_TOKEN'
    )

    foreach ($scope in @('Machine', 'User')) {
        foreach ($key in $keys) {
            $value = [System.Environment]::GetEnvironmentVariable($key, $scope)
            if (-not [string]::IsNullOrWhiteSpace($value)) {
                [System.Environment]::SetEnvironmentVariable($key, $value, 'Process')
            }
        }
    }
}

function Resolve-JavaCommand {
    $candidates = [System.Collections.Generic.List[string]]::new()
    if (-not [string]::IsNullOrWhiteSpace($env:JAVA_HOME)) {
        $candidates.Add((Join-Path $env:JAVA_HOME 'bin\java.exe'))
    }

    foreach ($baseDir in @('C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java')) {
        if (-not (Test-Path -LiteralPath $baseDir -PathType Container)) {
            continue
        }
        Get-ChildItem -LiteralPath $baseDir -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object { $candidates.Add((Join-Path $_.FullName 'bin\java.exe')) }
    }

    $pathJava = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($pathJava) {
        $candidates.Add($pathJava.Source)
    }

    foreach ($candidate in $candidates | Select-Object -Unique) {
        if (-not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
            continue
        }
        $versionOutput = & $candidate -version 2>&1
        $match = [regex]::Match(($versionOutput -join [Environment]::NewLine), '(?:openjdk |java )version "([^"]+)"')
        if (-not $match.Success) {
            continue
        }
        $parts = $match.Groups[1].Value.Split('.')
        $major = if ($parts[0] -eq '1' -and $parts.Length -gt 1) { [int] $parts[1] } else { [int] $parts[0] }
        if ($major -ge 17) {
            return $candidate
        }
    }

    throw 'Java 17 ou superior nao encontrado. O ETL nao pode ser executado com o java.exe legado do PATH.'
}

function Resolve-SqlConnection {
    if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
        throw 'DB_URL nao configurada para a materializacao noturna.'
    }

    $match = [regex]::Match(
        $env:DB_URL,
        '^jdbc:sqlserver://(?<server>[^;]+);(?<properties>.*)$',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $match.Success) {
        throw 'DB_URL nao segue o formato jdbc:sqlserver://servidor:porta;databaseName=banco;.'
    }

    $databaseMatch = [regex]::Match(
        $match.Groups['properties'].Value,
        '(?:^|;)databaseName=(?<database>[^;]+)',
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $databaseMatch.Success) {
        throw 'DB_URL nao informa databaseName.'
    }

    [PSCustomObject]@{
        Server = $match.Groups['server'].Value.Replace(':', ',')
        Database = $databaseMatch.Groups['database'].Value
        TrustServerCertificate = $match.Groups['properties'].Value -match '(?:^|;)trustServerCertificate=true(?:;|$)'
    }
}

function Invoke-SqlCommand {
    param(
        [Parameter(Mandatory)]
        [pscustomobject] $Connection,
        [Parameter(Mandatory)]
        [string] $Query
    )

    $arguments = @(
        '-I',
        '-f', '65001',
        '-S', $Connection.Server,
        '-d', $Connection.Database,
        '-b',
        '-Q', $Query
    )
    if ($Connection.TrustServerCertificate) {
        $arguments += '-C'
    }

    if ([string]::IsNullOrWhiteSpace($env:DB_USER)) {
        $arguments += '-E'
    } else {
        if ([string]::IsNullOrWhiteSpace($env:DB_PASSWORD)) {
            throw 'DB_USER definido, mas DB_PASSWORD esta vazio.'
        }
        $env:SQLCMDPASSWORD = $env:DB_PASSWORD
        $arguments += @('-U', $env:DB_USER)
    }

    try {
        & sqlcmd.exe @arguments *>> $LogPath
        if ($null -eq $LASTEXITCODE) {
            return 0
        }
        return [int] $LASTEXITCODE
    } finally {
        Remove-Item Env:SQLCMDPASSWORD -ErrorAction SilentlyContinue
    }
}

function Publish-FailureEvent {
    param(
        [int] $ExitCode,
        [string] $Message
    )

    $eventMessage = $Message
    if ($eventMessage.Length -gt 900) {
        $eventMessage = $eventMessage.Substring(0, 900)
    }

    try {
        & eventcreate.exe /T ERROR /ID 3300 /L APPLICATION /SO $EventSource /D $eventMessage | Out-Null
        Write-LogLine "Evento de falha publicado no Application Log com source $EventSource."
    } catch {
        Write-LogLine ("Nao foi possivel publicar evento de falha: {0}" -f $_.Exception.Message)
    }

    try {
        Set-Content -LiteralPath $FailureMarker -Encoding UTF8 -Value @(
            "exit_code=$ExitCode",
            "log=$LogPath",
            "timestamp=$(Get-Date -Format o)",
            "message=$Message"
        )
    } catch {
        Write-LogLine ("Nao foi possivel atualizar marcador de falha: {0}" -f $_.Exception.Message)
    }
}

function Invoke-MaterializacaoFatosBi {
    $script:MaterializacaoExitCode = 0

    if ($env:ETL_SKIP_CARGA_GESTAO_VISTA_NOTURNA -eq '1') {
        Write-LogLine "Cargas materializadas BI (Gestao a Vista, Faturamento de Fretes e Faturas por Cliente) ignoradas por ETL_SKIP_CARGA_GESTAO_VISTA_NOTURNA=1."
        return
    }

    Write-LogLine "Iniciando cargas materializadas BI (Gestao a Vista, Faturamento de Fretes e Faturas por Cliente)."
    try {
        if (-not (Get-Command sqlcmd.exe -ErrorAction SilentlyContinue)) {
            throw 'sqlcmd.exe nao encontrado no PATH.'
        }

        $connection = Resolve-SqlConnection
        $queries = @(
            @"
IF NOT EXISTS (
    SELECT 1
    FROM sys.columns
    WHERE object_id = OBJECT_ID(N'dbo.vw_fretes_powerbi')
      AND name = N'Responsável Região Destino Key'
)
    THROW 53001, 'Contrato invalido: dbo.vw_fretes_powerbi sem Responsável Região Destino Key.', 1;
"@,
            'EXEC dbo.sp_carga_fato_gestao_vista_fretes;',
            'EXEC dbo.sp_carga_fato_gestao_vista_coletores;',
            'EXEC dbo.sp_carga_fato_fretes_faturamento;',
            'EXEC dbo.sp_carga_fato_gestao_vista_faturas;',
            'EXEC dbo.sp_carga_fato_gestao_vista_manifestos;'
        )

        $dbExitCode = 0
        foreach ($query in $queries) {
            $dbExitCode = Invoke-SqlCommand -Connection $connection -Query $query
            if ($dbExitCode -ne 0) {
                break
            }
        }
    } catch {
        Write-LogLine ("Falha ao preparar materializacao BI: {0}" -f $_.Exception.Message)
        $dbExitCode = 3
    }

    if ($dbExitCode -ne 0) {
        $message = "Cargas materializadas BI falharam com exit code $dbExitCode. Consulte $LogPath."
        Write-LogLine $message
        Publish-FailureEvent -ExitCode $dbExitCode -Message $message
        $script:MaterializacaoExitCode = $dbExitCode
        return
    }

    Write-LogLine "Cargas materializadas BI concluidas com sucesso."
}

New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
Set-Location -LiteralPath $RepoRoot
Import-OperationalEnvironment

Write-LogLine "Iniciando expurgo logico noturno."
Write-LogLine "RepoRoot=$RepoRoot"
Write-LogLine "JarPath=$JarPath"

if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
    $message = "JAR nao encontrado em $JarPath. Execute mvn package -DskipTests antes da janela noturna."
    Write-LogLine $message
    Publish-FailureEvent -ExitCode 2 -Message $message
    $exitCode = 2
} else {
    $javaExe = Resolve-JavaCommand
    $javaArgs = @(
        '--enable-native-access=ALL-UNNAMED',
        "-DETL_BASE_DIR=$RepoRoot",
        "-Detl.base.dir=$RepoRoot",
        '-jar',
        $JarPath,
        '--expurgo-orfaos',
        '--batch-size',
        '500'
    )

    Write-LogLine ("Executando: {0} {1}" -f $javaExe, (($javaArgs | ForEach-Object {
        if ($_ -like '* *') { '"' + $_ + '"' } else { $_ }
    }) -join ' '))

    & $javaExe @javaArgs *>> $LogPath
    $exitCode = if ($null -eq $LASTEXITCODE) { 0 } else { [int] $LASTEXITCODE }
}

if ($exitCode -eq 0) {
    Write-LogLine "Expurgo logico noturno concluido com sucesso."
} else {
    $failureMessage = "Expurgo logico noturno falhou com exit code $exitCode. Cargas materializadas BI serao executadas mesmo assim. Consulte $LogPath."
    Write-LogLine $failureMessage
    Publish-FailureEvent -ExitCode $exitCode -Message $failureMessage
}

Invoke-MaterializacaoFatosBi

if ($exitCode -eq 0 -and $script:MaterializacaoExitCode -eq 0) {
    if (Test-Path -LiteralPath $FailureMarker -PathType Leaf) {
        Remove-Item -LiteralPath $FailureMarker -Force
    }
    exit 0
}

if ($script:MaterializacaoExitCode -ne 0) {
    exit $script:MaterializacaoExitCode
}

exit $exitCode
