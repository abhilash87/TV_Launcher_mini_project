param(
    [Parameter(Mandatory = $true)]
    [string]$PhotoPath
)
$ErrorActionPreference = 'Stop'
$taskPhoto = Get-Item -LiteralPath $PhotoPath
$taskExtension = $taskPhoto.Extension.ToLowerInvariant()
if ($taskExtension -notin @('.jpg', '.jpeg', '.png', '.webp')) {
    throw 'Choose a JPG, PNG, or WebP image. Convert HEIC photos first.'
}
$taskPrivateFolder = Join-Path $PSScriptRoot 'tv-home-test\app\src\debug\res\drawable-nodpi'
New-Item -ItemType Directory -Force -Path $taskPrivateFolder | Out-Null
$taskTarget = Join-Path $taskPrivateFolder ("family_bg" + $taskExtension)
$taskSourceFull = [System.IO.Path]::GetFullPath($taskPhoto.FullName)
if ($taskSourceFull -ieq [System.IO.Path]::GetFullPath($taskTarget)) {
    throw 'Choose your original photo outside the app resources.'
}
# Debug-only resources override the neutral main resource without changing it.
Copy-Item -LiteralPath $taskPhoto.FullName -Destination $taskTarget -Force
foreach ($taskFolder in @($taskPrivateFolder)) {
    foreach ($taskOldFile in @(Get-ChildItem -LiteralPath $taskFolder -File -Filter 'family_bg.*')) {
        if ($taskOldFile.FullName -ine $taskTarget) {
            Remove-Item -LiteralPath $taskOldFile.FullName
        }
    }
}
Write-Output 'Private wallpaper copied. Rebuild the APK to use it.'
Write-Output 'The image is ignored by Git. The public neutral background is unchanged.'
