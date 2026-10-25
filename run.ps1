param(
    [ValidateSet('desktop', 'api', 'test', 'build')][string]$Mode = 'desktop',
    [string]$Database = 'library.db',
    [switch]$CoreOnly,
    [ValidateRange(1, 65535)][int]$Port = 8080
)
$ErrorActionPreference = 'Stop'
Push-Location $PSScriptRoot
try {
    $jdkBin = $null
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
        $jdkBin = Join-Path $env:JAVA_HOME 'bin'
    } else {
        $installed = @(Get-ChildItem 'C:/Program Files/Java', 'C:/Program Files/Eclipse Adoptium', 'C:/Program Files/Microsoft' -Directory -ErrorAction SilentlyContinue |
            Where-Object { Test-Path (Join-Path $_.FullName 'bin/javac.exe') } |
            Sort-Object LastWriteTime -Descending)
        if ($installed.Count -gt 0) { $jdkBin = Join-Path $installed[0].FullName 'bin' }
    }
    $compiler = if ($jdkBin) { Join-Path $jdkBin 'javac.exe' } else { 'javac' }
    $runtime = if ($jdkBin) { Join-Path $jdkBin 'java.exe' } else { 'java' }
    if (-not (Get-ChildItem 'lib/sqlite-jdbc-*.jar' -ErrorAction SilentlyContinue)) {
        throw 'SQLite JDBC driver is missing from lib/.'
    }
    New-Item -ItemType Directory -Force out | Out-Null
    $sources = @(Get-ChildItem src/main/java -Filter *.java -Recurse | ForEach-Object { $_.FullName })
    & $compiler --release 17 -encoding UTF-8 -cp 'lib/*' -d out @sources
    if ($LASTEXITCODE -ne 0) { throw 'Java compilation failed.' }
    switch ($Mode) {
        'build' { Write-Host 'SoftwareEngineeing compiled successfully.' }
        'api' { & $runtime -cp 'out;lib/*' com.example.library.ApiMain $Database $Port }
        'desktop' { & $runtime -cp 'out;lib/*' com.example.library.Main $Database }
        'test' {
            $tests = @(Get-ChildItem src/test/java -Filter *.java -Recurse | ForEach-Object { $_.FullName })
            & $compiler --release 17 -encoding UTF-8 -cp 'out;lib/*' -d out @tests
            if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed.' }
            $testArgs = @()
            if ($CoreOnly) { $testArgs += '--core-only' }
            & $runtime '-Djava.awt.headless=true' -cp 'out;lib/*' com.example.library.LibraryIntegrationTest @testArgs
        }
    }
    if ($LASTEXITCODE -ne 0) { throw "Java exited with code $LASTEXITCODE." }
} finally {
    Pop-Location
}
