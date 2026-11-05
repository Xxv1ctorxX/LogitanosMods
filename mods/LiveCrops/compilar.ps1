param([switch]$Offline)
$ErrorActionPreference = 'Stop'
$taskTemp = Join-Path $PSScriptRoot 'build\tmp'
New-Item -ItemType Directory -Force -Path $taskTemp | Out-Null
$previousJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    # Evita el fallo AF_UNIX del JDK de Windows con rutas temporales del entorno.
    $env:JAVA_TOOL_OPTIONS = ($previousJavaOptions + ' -Djdk.net.unixdomain.tmpdir="' + $taskTemp + '"').Trim()
    Push-Location $PSScriptRoot
    try {
        $buildArguments = @('--no-daemon', 'build', 'collectJar')
        if ($Offline) { $buildArguments = @('--offline') + $buildArguments }
        & (Join-Path $PSScriptRoot 'gradlew.bat') @buildArguments
        if ($LASTEXITCODE -ne 0) { throw "La compilación terminó con código $LASTEXITCODE" }
    } finally { Pop-Location }
} finally { $env:JAVA_TOOL_OPTIONS = $previousJavaOptions }
