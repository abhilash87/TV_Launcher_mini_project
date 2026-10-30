$ErrorActionPreference = 'Stop'
Push-Location (Join-Path $PSScriptRoot 'tv-home-test')
try {
    & .\gradlew.bat --no-daemon --console=plain assembleDebug lintDebug testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
    Write-Output 'APK: tv-home-test\app\build\outputs\apk\debug\app-debug.apk'
} finally {
    Pop-Location
}
