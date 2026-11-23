# Install the newest TV Home V2 build on the TV: downloads the signed APK from the rolling "v2-latest" GitHub
# Release (published by the "V2 release" workflow) and installs it as an update (adb install -r keeps its settings), then opens it.
#
# Usage: Scripts\Install-Package.ps1 -TvHost 192.168.1.50     (your TV's IP address; or set $env:TV_HOST once)
#        [-Repo owner/name]        GitHub repository to download from; by default the one this clone came from
#        [-Apk .\app-release.apk]  install this APK instead of downloading one
#        [-Branch main]            branch whose newest build to use
#        [-AllowDowngrade]         allow installing an older build than the one on the TV (adb install -d)
#        [-SetHome]                make V2 the Home app (and turn the previous launcher, V1, off if it is installed)
#        [-RollbackToV1]           turn V1 back on and make it Home again, if you have V1; installs nothing
#        [-OpenSettings]           open V2's own Settings on the TV after installing
#        [-Reinstall]              uninstall V2 first. WIPES V2'S SETTINGS: only for a build signed with a new key
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Repo = '',
    [string]$Apk = '',
    [string]$Branch = 'main',
    [switch]$AllowDowngrade,
    [switch]$SetHome,
    [switch]$RollbackToV1,
    [switch]$OpenSettings,
    [switch]$Reinstall
)
# Continue, not Stop: Windows PowerShell 5.1 turns native stderr output into terminating errors under Stop.
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'TvAdb.ps1')
$v2 = 'com.chakriau.tvhome.v2'
$v1 = 'com.chakriau.tvhome'

Initialize-TvAdb -TvHost $TvHost -NeedGh:(-not $Apk -and -not $RollbackToV1)

if ($RollbackToV1) {
    if (-not (Get-InstalledVersion $v1)) { throw "V1 ($v1) is not installed on this TV, so there is nothing to roll back to." }
    Adb shell pm enable $v1 | Out-Null
    Adb shell cmd package set-home-activity "$v1/.MainActivity" | Out-Null
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    Write-Host 'V1 is Home again. V2 stays installed.'
    return
}

if (-not $Apk) {
    Write-Host "Repository: $(Resolve-TvRepo -Repo $Repo)"
    $Apk = Get-LatestApk -Workflow v2-release.yml -Artifact tvhome-v2 -Release v2-latest -Branch $Branch `
        -Dir (Join-Path $PSScriptRoot 'builds\latest')
}
Write-Host "APK: $Apk"

$before = Get-InstalledVersion $v2
Write-Host "On the TV: $(if ($before) { "V2 $before" } else { 'V2 not installed' })"
if ($Reinstall -and $before) {
    Write-Host 'Uninstalling V2 first (its settings are wiped)...'
    Adb uninstall $v2 | Out-Null
}

Write-Host 'Installing...'
$installArgs = @('install', '-r') + $(if ($AllowDowngrade) { @('-d') } else { @() }) + @($Apk)
$result = (Adb @installArgs -Quiet) -join ' '
if ($LASTEXITCODE -ne 0 -or $result -notmatch 'Success') {
    if ($result -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
        throw 'The V2 on the TV is signed with a different key. Run again with -Reinstall (this wipes V2''s settings).'
    }
    if ($result -match 'INSTALL_FAILED_VERSION_DOWNGRADE') {
        throw 'This build is older than the V2 on the TV. Run again with -AllowDowngrade to install it anyway.'
    }
    throw "Install failed: $result"
}
Write-Host "Installed: V2 $(Get-InstalledVersion $v2)"

# Continue watching reads every app's Play Next items (Documents/Requirements.md 5.7). Home asks once on the TV too; granting it
# here saves the dialog. It shows (and can be asked for again) in Settings › Continue watching.
Adb shell pm grant $v2 android.permission.READ_TV_LISTINGS -Quiet | Out-Null

if ($SetHome) {
    $homes = (Adb shell cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME) -join "`n"
    if ($homes -notmatch [regex]::Escape("$v2/")) { throw 'This build is not registered as a Home app on the TV.' }
    Adb shell cmd package set-home-activity "$v2/.MainActivity" | Out-Null
    # V1's Home-button helper keeps sending Home to V1 unless V1 is off (Documents/Requirements.md section 9). Kept
    # installed. -Quiet: on a TV without V1 this does nothing.
    Adb shell pm disable-user --user 0 $v1 -Quiet | Out-Null
    Adb shell input keyevent KEYCODE_HOME | Out-Null
    Write-Host 'V2 is now the Home app (V1, if installed, is turned off; undo with -RollbackToV1).'
} else {
    Adb shell am start -n "$v2/.MainActivity" | Out-Null
    Write-Host 'Opened V2 on the TV.'
}

if ($OpenSettings) {
    Adb shell am start -n "$v2/.settings.SettingsActivity" | Out-Null
    Write-Host 'Opened V2 Settings on the TV.'
}
