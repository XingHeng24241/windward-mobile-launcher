[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$RepoRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$Failures = [System.Collections.Generic.List[string]]::new()

function Add-Failure {
    param([string]$Message)
    $script:Failures.Add($Message)
}

function Assert-FileExists {
    param([string]$RelativePath)
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure "Missing required file: $RelativePath"
    }
}

function Assert-FileMissing {
    param([string]$RelativePath)
    $path = Join-Path $RepoRoot $RelativePath
    if (Test-Path -LiteralPath $path) {
        Add-Failure "File should be removed: $RelativePath"
    }
}

function Assert-Text {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [string]$Description
    )
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        Add-Failure "Cannot check $Description because file is missing: $RelativePath"
        return
    }
    $content = Get-Content -Raw -LiteralPath $path
    if ($content -notmatch $Pattern) {
        Add-Failure "Expected $Description in $RelativePath"
    }
}

function Assert-NoText {
    param(
        [string]$RelativePath,
        [string]$Pattern,
        [string]$Description
    )
    $path = Join-Path $RepoRoot $RelativePath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return
    }
    $content = Get-Content -Raw -LiteralPath $path
    if ($content -match $Pattern) {
        Add-Failure "Unexpected $Description in $RelativePath"
    }
}

function Assert-LiteralAbsentFromTree {
    param(
        [string]$Literal,
        [string[]]$RelativeRoots
    )
    foreach ($relativeRoot in $RelativeRoots) {
        $path = Join-Path $RepoRoot $relativeRoot
        if (-not (Test-Path -LiteralPath $path)) {
            continue
        }
        $matches = & rg --files-with-matches --fixed-strings --glob '!**/build/**' -- $Literal $path 2>$null
        if ($LASTEXITCODE -eq 0) {
            foreach ($match in $matches) {
                Add-Failure "Disabled endpoint still present: $Literal in $match"
            }
        }
    }
}

$buildFile = 'FCL/build.gradle.kts'
Assert-Text $buildFile 'applicationId\s*=\s*"com\.windward\.launcher"' 'Windward applicationId'
Assert-Text $buildFile 'versionCode\s*=\s*1347' 'Windward versionCode'
Assert-Text $buildFile 'versionName\s*=\s*"1\.3\.4\.5-Windward\.3"' 'Windward versionName'
Assert-Text $buildFile 'com\.windward\.launcher\.debug\.provider' 'Windward debug provider authority'
Assert-Text $buildFile 'Windward-Mobile-Launcher-' 'Windward APK output name'

Assert-Text 'FCL/src/main/res/values/strings.xml' '<string name="app_name" translatable="false">Windward 手机版启动器</string>' 'Windward application label'
Assert-Text 'FCL/src/main/res/values/strings.xml' '<string name="file_browser_provider" translatable="false">com\.windward\.launcher\.provider</string>' 'Windward release provider authority'
Assert-NoText 'FCL/src/main/java/com/tungsten/fcl/activity/MainActivity.kt' 'UpdateChecker|checkAuto' 'launcher automatic update call'
Assert-NoText 'FCL/src/main/java/com/tungsten/fcl/ui/setting/LauncherSettingAdapter.kt' 'CHECK_UPDATE|SHOW_CHANGELOG|settings_launcher_upgrade' 'launcher update setting row'
Assert-NoText 'FCL/src/main/java/com/tungsten/fcl/ui/setting/LauncherSettingPage.java' 'UpdateChecker|CHECK_UPDATE|SHOW_CHANGELOG' 'launcher update setting dispatch'
Assert-NoText 'FCL/src/main/java/com/tungsten/fcl/ui/main/MainUI.java' 'ANNOUNCEMENT_URL|checkAnnouncement|announcement_v2' 'official announcement request'

$forbiddenEndpoints = @(
    'https://raw.githubusercontent.com/FCL-Team/FoldCraftLauncher/main/version_map.json',
    'https://gitee.com/fcl-team/FCL-Repo/raw/main/res/version_map.json',
    'https://raw.githubusercontent.com/FCL-Team/FCL-Repo/refs/heads/main/res/announcement_v2.txt',
    'https://gitee.com/fcl-team/FCL-Repo/raw/main/res/announcement_v2.txt'
)
$compiledRoots = @('FCL/src/main/java', 'FCL/src/main/res', 'FCL/src/main/assets')
foreach ($endpoint in $forbiddenEndpoints) {
    Assert-LiteralAbsentFromTree $endpoint $compiledRoots
}

Assert-FileMissing 'FCL/src/main/java/com/tungsten/fcl/upgrade/UpdateChecker.java'
Assert-FileMissing 'FCL/src/main/java/com/tungsten/fcl/upgrade/UpdateDialog.java'
Assert-FileMissing 'FCL/src/main/java/com/tungsten/fcl/upgrade/ChangelogDialog.java'
Assert-FileMissing 'FCL/src/main/java/com/tungsten/fcl/upgrade/RemoteVersion.java'
Assert-FileMissing 'FCL/src/main/java/com/tungsten/fcl/ui/main/Announcement.java'

$preservedFiles = @(
    'FCL/src/main/java/com/tungsten/fclcore/download/DefaultGameBuilder.java',
    'FCL/src/main/java/com/tungsten/fclcore/download/game/GameInstallTask.java',
    'FCL/src/main/java/com/tungsten/fclcore/download/fabric/FabricInstallTask.java',
    'FCL/src/main/java/com/tungsten/fclcore/download/forge/ForgeInstallTask.java',
    'FCL/src/main/java/com/tungsten/fclcore/download/neoforge/NeoForgeInstallTask.java',
    'FCL/src/main/java/com/tungsten/fclcore/mod/modrinth/ModrinthRemoteModRepository.java',
    'FCL/src/main/java/com/tungsten/fclcore/mod/curse/CurseForgeRemoteModRepository.java',
    'FCL/src/main/java/com/mio/download/DownloadManager.kt'
)
foreach ($file in $preservedFiles) {
    Assert-FileExists $file
}

$iconPath = Join-Path $RepoRoot 'FCL/src/main/res/drawable-nodpi/windward_launcher_source.jpg'
if (-not (Test-Path -LiteralPath $iconPath -PathType Leaf)) {
    Add-Failure 'Missing Windward icon source resource'
} else {
    $actualHash = (Get-FileHash -Algorithm SHA256 -LiteralPath $iconPath).Hash
    $expectedHash = '0B53F1CE193530612686586C0624067FE5D03E15BF7D16800724E5DD51A4DED9'
    if ($actualHash -ne $expectedHash) {
        Add-Failure "Windward icon hash mismatch: $actualHash"
    }
}

Assert-FileExists 'LICENSE'
Assert-FileExists 'MODIFICATIONS.md'
Assert-Text '.gitignore' 'WINDWARD|windward|\*\.pepk' 'Windward secret ignore rules'

if ($Failures.Count -gt 0) {
    Write-Host "Windward customization verification failed ($($Failures.Count) issue(s)):" -ForegroundColor Red
    foreach ($failure in $Failures) {
        Write-Host " - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host 'Windward customization verification passed.' -ForegroundColor Green
exit 0
