param(
    [Parameter(Mandatory = $true)]
    [string] $RepoRoot
)

$ErrorActionPreference = 'SilentlyContinue'

function Resolve-FullPath {
    param([string] $Path)
    return [System.IO.Path]::GetFullPath($Path).TrimEnd('\', '/')
}

function Normalize-Text {
    param([string] $Value)
    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ''
    }
    return $Value.Replace('\', '/').ToLowerInvariant()
}

function Read-PropertiesFile {
    param([string] $Path)

    $properties = @{}
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        return $properties
    }

    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $trimmed = $line.Trim()
        if ($trimmed.Length -eq 0 -or $trimmed.StartsWith('#')) {
            continue
        }
        $separator = $trimmed.IndexOf('=')
        if ($separator -lt 0) {
            continue
        }
        $key = $trimmed.Substring(0, $separator).Trim()
        $value = $trimmed.Substring($separator + 1).Trim()
        if ($key.Length -gt 0) {
            $properties[$key] = $value
        }
    }
    return $properties
}

function Get-ProcessCommandLine {
    param([int] $ProcessId)

    $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId"
    if ($processInfo) {
        return [string] $processInfo.CommandLine
    }
    return ''
}

function Test-ProcessAlive {
    param([int] $ProcessId)
    return $null -ne (Get-Process -Id $ProcessId -ErrorAction SilentlyContinue)
}

function Get-JavaProcessInfos {
    $items = @()
    foreach ($name in @('java.exe', 'javaw.exe')) {
        $items += Get-CimInstance Win32_Process -Filter "Name = '$name'"
    }
    return $items
}

function Mask-CommandLine {
    param([string] $CommandLine)

    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return '(linha de comando indisponivel)'
    }

    $masked = $CommandLine
    $masked = [regex]::Replace($masked, '(?i)(-D[^=\s]*(password|senha|token|secret)[^=\s]*=)(\"[^\"]*\"|[^\s]+)', '$1***')
    $masked = [regex]::Replace($masked, '(?i)((password|senha|token|secret)\s*=\s*)(\"[^\"]*\"|[^\s]+)', '$1***')
    if ($masked.Length -gt 260) {
        return $masked.Substring(0, 257) + '...'
    }
    return $masked
}

function Get-CommandTokens {
    param([string] $CommandLine)

    if ([string]::IsNullOrWhiteSpace($CommandLine)) {
        return @()
    }

    return [regex]::Matches($CommandLine, '"[^"]*"|[^\s]+') | ForEach-Object {
        $_.Value.Trim('"')
    }
}

function Add-Conflict {
    param(
        [hashtable] $Conflicts,
        [int] $ProcessId,
        [string] $Kind,
        [string] $Source,
        [string] $Status,
        [string] $Flag,
        [string] $CommandLine
    )

    $key = [string] $ProcessId
    if ($Conflicts.ContainsKey($key)) {
        $current = $Conflicts[$key]
        if ($current.Kind -ne 'DAEMON' -and $Kind -eq 'DAEMON') {
            $current.Kind = $Kind
        }
        if ([string]::IsNullOrWhiteSpace($current.Status) -and -not [string]::IsNullOrWhiteSpace($Status)) {
            $current.Status = $Status
        }
        if ([string]::IsNullOrWhiteSpace($current.Flag) -and -not [string]::IsNullOrWhiteSpace($Flag)) {
            $current.Flag = $Flag
        }
        if ($current.CommandLine -eq '(linha de comando indisponivel)' -and -not [string]::IsNullOrWhiteSpace($CommandLine)) {
            $current.CommandLine = Mask-CommandLine $CommandLine
        }
        return
    }

    $Conflicts[$key] = [pscustomobject] @{
        Pid = $ProcessId
        Kind = $Kind
        Source = $Source
        Status = $Status
        Flag = $Flag
        CommandLine = Mask-CommandLine $CommandLine
    }
}

function Find-FirstExecutionFlag {
    param([string] $CommandLine)

    $flags = @(
        '--loop-daemon-run',
        '--executar-step-isolado',
        '--validar-api-banco-24h-detalhado',
        '--validar-api-banco-24h',
        '--validar-etl-extremo',
        '--fluxo-completo',
        '--extracao-intervalo',
        '--testar-api',
        '--recovery'
    )

    foreach ($token in Get-CommandTokens $CommandLine) {
        $normalizedToken = Normalize-Text $token
        if ($normalizedToken.StartsWith('-d')) {
            continue
        }
        foreach ($flag in $flags) {
            if ($normalizedToken.Equals($flag, [System.StringComparison]::OrdinalIgnoreCase)) {
                return $flag
            }
        }
    }
    return ''
}

function Test-SkipCommand {
    param([string] $CommandLine)

    $flag = Find-FirstExecutionFlag $CommandLine
    foreach ($skipFlag in @(
        '--auth-check',
        '--auth-bootstrap',
        '--loop-daemon-start',
        '--loop-daemon-stop',
        '--loop-daemon-status',
        '--ajuda',
        '--help'
    )) {
        if ($flag.Equals($skipFlag, [System.StringComparison]::OrdinalIgnoreCase)) {
            return $true
        }
    }
    return $false
}

$repoFull = Resolve-FullPath $RepoRoot
$repoNorm = Normalize-Text $repoFull
$runtimeDir = Join-Path $repoFull 'logs\daemon\runtime'
$stateFile = Join-Path $runtimeDir 'loop_daemon.state'
$pidFile = Join-Path $runtimeDir 'loop_daemon.pid'
$conflicts = @{}

$state = Read-PropertiesFile $stateFile
$daemonStatus = ''
if ($state.ContainsKey('status')) {
    $daemonStatus = [string] $state['status']
}

$candidateDaemonPids = New-Object 'System.Collections.Generic.List[int]'
if (Test-Path -LiteralPath $pidFile -PathType Leaf) {
    $pidText = (Get-Content -LiteralPath $pidFile -Encoding UTF8 -TotalCount 1).Trim()
    $pidValue = 0
    if ([int]::TryParse($pidText, [ref] $pidValue)) {
        $candidateDaemonPids.Add($pidValue)
    }
}
if ($state.ContainsKey('pid')) {
    $pidValue = 0
    if ([int]::TryParse([string] $state['pid'], [ref] $pidValue)) {
        $candidateDaemonPids.Add($pidValue)
    }
}

foreach ($candidatePid in ($candidateDaemonPids | Select-Object -Unique)) {
    if (-not (Test-ProcessAlive $candidatePid)) {
        continue
    }
    $commandLine = Get-ProcessCommandLine $candidatePid
    $isLoopDaemon = (Find-FirstExecutionFlag $commandLine) -eq '--loop-daemon-run'
    $persistedAsActiveDaemon = -not [string]::IsNullOrWhiteSpace($daemonStatus) -and
        -not $daemonStatus.Equals('STOPPED', [System.StringComparison]::OrdinalIgnoreCase)

    if ($isLoopDaemon -or $persistedAsActiveDaemon) {
        Add-Conflict $conflicts $candidatePid 'DAEMON' 'arquivo de estado do daemon' $daemonStatus '--loop-daemon-run' $commandLine
    }
}

foreach ($processInfo in Get-JavaProcessInfos) {
    $processId = [int] $processInfo.ProcessId
    $commandLine = [string] $processInfo.CommandLine
    $normalized = Normalize-Text $commandLine
    if ($normalized.Length -eq 0) {
        continue
    }
    $looksLikeExtrator = $normalized.Contains($repoNorm) -or
        $normalized.Contains('extrator.jar') -or
        $normalized.Contains('extrator-daemon-runtime') -or
        $normalized.Contains('br.com.extrator.bootstrap.main')
    if (-not $looksLikeExtrator) {
        continue
    }
    if (Test-SkipCommand $commandLine) {
        continue
    }

    $flag = Find-FirstExecutionFlag $commandLine
    if ([string]::IsNullOrWhiteSpace($flag)) {
        continue
    }

    $kind = 'EXTRACAO'
    if ($flag -eq '--loop-daemon-run') {
        $kind = 'DAEMON'
    } elseif ($flag -eq '--executar-step-isolado') {
        $kind = 'STEP_ISOLADO'
    } elseif ($flag.StartsWith('--validar-')) {
        $kind = 'VALIDACAO'
    } elseif ($flag -eq '--testar-api') {
        $kind = 'TESTE_API'
    } elseif ($flag -eq '--recovery') {
        $kind = 'RECOVERY'
    }

    Add-Conflict $conflicts $processId $kind 'processo Java ativo' '' $flag $commandLine
}

if ($conflicts.Count -eq 0) {
    exit 0
}

Write-Host ''
Write-Host '================================================================'
Write-Host 'ATENCAO: ja existe execucao ativa do Extrator ESL'
Write-Host '================================================================'
Write-Host 'Abrir outra extracao em paralelo pode disputar banco, APIs e locks.'
Write-Host ''

foreach ($item in ($conflicts.Values | Sort-Object Kind, Pid)) {
    $statusText = if ([string]::IsNullOrWhiteSpace($item.Status)) { 'desconhecido' } else { $item.Status }
    $flagText = if ([string]::IsNullOrWhiteSpace($item.Flag)) { 'sem flag identificada' } else { $item.Flag }
    Write-Host ("  - tipo={0} pid={1} status={2} comando={3}" -f $item.Kind, $item.Pid, $statusText, $flagText)
    Write-Host ("    {0}" -f $item.CommandLine)
}

Write-Host ''
Write-Host 'Use o menu para cancelar, parar o daemon e revalidar, ou continuar conscientemente.'
Write-Host 'Para automacoes controladas, EXTRATOR_ALLOW_CONCURRENT_RUN=1 libera a execucao.'
Write-Host '================================================================'
exit 2
