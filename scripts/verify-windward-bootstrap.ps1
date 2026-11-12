[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure {
    param([string]$Message)
    $script:Failures.Add($Message)
}

function Assert-Text {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [string]$Description
    )
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure "Missing file for ${Description}: $RelativePath"
        return
    }
    if ((Get-Content -Raw -LiteralPath $path) -notmatch $Pattern) {
        Add-Failure "Missing ${Description} in $RelativePath"
    }
}

$assetRelative = 'FCL/src/main/assets/windward/modpack.mrpack'
$assetPath = Join-Path $RepoRoot $assetRelative
$expectedHash = 'D84FAA9172EB2561C323EB14E9AFC2850303DD3EDBCA42D0BACEE0BCC327379F'
if (-not (Test-Path -LiteralPath $assetPath -PathType Leaf)) {
    Add-Failure "Missing bundled MRPACK: $assetRelative"
} else {
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetPath).Hash
    if ($actualHash -ne $expectedHash) {
        Add-Failure "Bundled MRPACK hash mismatch: $actualHash"
    }

    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $archive = [System.IO.Compression.ZipFile]::OpenRead($assetPath)
    try {
        $entry = $archive.GetEntry('modrinth.index.json')
        if ($null -eq $entry) {
            Add-Failure 'Bundled MRPACK has no modrinth.index.json'
        } else {
            $reader = [System.IO.StreamReader]::new($entry.Open())
            try {
                $manifest = ($reader.ReadToEnd() | ConvertFrom-Json)
                if ($manifest.name -ne 'Windward 客户端') {
                    Add-Failure "Unexpected pack name: $($manifest.name)"
                }
                if ($manifest.versionId -ne '1.0.4') {
                    Add-Failure "Unexpected pack version: $($manifest.versionId)"
                }
                if ($manifest.dependencies.minecraft -ne '1.21.11') {
                    Add-Failure "Unexpected Minecraft version: $($manifest.dependencies.minecraft)"
                }
                if ($manifest.dependencies.'fabric-loader' -ne '0.19.5') {
                    Add-Failure "Unexpected Fabric version: $($manifest.dependencies.'fabric-loader')"
                }
            } finally {
                $reader.Dispose()
            }
        }
    } finally {
        $archive.Dispose()
    }
}

Assert-Text 'FCL/src/main/java/com/tungsten/fcl/game/WindwardModpackBootstrap.java' `
    'ASSET_SHA256[\s\S]*D84FAA9172EB2561C323EB14E9AFC2850303DD3EDBCA42D0BACEE0BCC327379F' `
    'fixed MRPACK identity'
Assert-Text 'FCL/src/main/java/com/tungsten/fcl/game/WindwardModpackBootstrap.java' `
    'CONFIGURATION_FILE\s*=\s*"modpack\.cfg"' 'actual FCL modpack configuration filename'
Assert-Text 'FCL/src/main/java/com/tungsten/fcl/game/WindwardModpackBootstrap.java' `
    'recoverCompletedInstallationAtGameDirectory' 'safe completed-install recovery'
Assert-Text 'FCL/src/main/java/com/tungsten/fcl/activity/SplashActivity.kt' `
    'WindwardModpackBootstrap\.isInstalled' 'bootstrap completion gate'
Assert-Text 'FCL/src/main/java/com/tungsten/fcl/fragment/RuntimeFragment.kt' `
    'WindwardModpackBootstrap\.createInstallTask' 'standard first-run install call'
Assert-Text 'FCL/src/main/res/layout/fragment_runtime.xml' `
    '@\+id/modpack_progress' 'modpack progress row'
Assert-Text 'FCL/src/main/res/drawable/windward_launcher_foreground.xml' `
    'android:inset="18dp"' 'adaptive icon safety inset'
Assert-Text 'FCL/src/main/res/mipmap/windward_launcher.xml' `
    'android:left="4dp"' 'legacy icon safety inset'
Assert-Text 'FCL/src/main/res/values-v31/themes.xml' `
    'android:windowSplashScreenAnimatedIcon">@drawable/windward_launcher_foreground' `
    'Android 12 Windward splash icon'

if ($Failures.Count -gt 0) {
    Write-Host "Windward bootstrap verification failed ($($Failures.Count) issue(s)):" -ForegroundColor Red
    foreach ($failure in $Failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host 'Windward bootstrap verification passed.' -ForegroundColor Green
exit 0
