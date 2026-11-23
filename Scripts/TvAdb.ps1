# Shared helpers for the V2 PowerShell scripts (Install-Package.ps1, Setup-Signing.ps1). Dot-source it:
#     . (Join-Path $PSScriptRoot 'TvAdb.ps1')
# then call Initialize-TvAdb -TvHost <ip>. Needs adb (Android SDK Platform-Tools) on PATH, and gh for downloads.

# The GitHub repository the scripts work with ("owner/name"), set by Resolve-TvRepo.
$global:TvRepo = $null

function Resolve-TvRepo {
    # Works out which GitHub repository this copy of the launcher belongs to and sets $global:TvRepo, so the scripts
    # work in anyone's fork or copy. In order: -Repo, $env:TV_REPO, the git remote "origin" of the clone the script
    # is in, then gh's idea of the current repository.
    param([string]$Repo)
    if (-not $Repo) { $Repo = $env:TV_REPO }
    if (-not $Repo -and (Get-Command git -CommandType Application -ErrorAction SilentlyContinue)) {
        $url = git -C $PSScriptRoot remote get-url origin 2> $null
        if ("$url" -match 'github\.com[:/]+([^/]+)/([^/]+?)(\.git)?/?$') { $Repo = "$($Matches[1])/$($Matches[2])" }
    }
    if (-not $Repo -and (Get-Command gh -CommandType Application -ErrorAction SilentlyContinue)) {
        Push-Location $PSScriptRoot
        try { $Repo = gh repo view --json nameWithOwner -q .nameWithOwner 2> $null } finally { Pop-Location }
    }
    if (-not $Repo) {
        throw 'Could not tell which GitHub repository to use. Run the script from your clone of it, or pass -Repo <owner>/<name> (or set $env:TV_REPO).'
    }
    $global:TvRepo = "$Repo".Trim()
    return $global:TvRepo
}

function Initialize-TvAdb {
    # Checks the tools, starts the ADB server and connects to the TV. Sets $global:TvSerial and $global:TvAdbExe for Adb.
    param([string]$TvHost, [switch]$NeedGh)
    if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
    $global:OutputEncoding = New-Object System.Text.UTF8Encoding $false
    # Show gh's UTF-8 output (commit titles) correctly in the console.
    [Console]::OutputEncoding = New-Object System.Text.UTF8Encoding $false
    foreach ($tool in @('adb') + $(if ($NeedGh) { @('gh') } else { @() })) {
        if (-not (Get-Command $tool -CommandType Application -ErrorAction SilentlyContinue)) {
            throw "$tool is not on PATH. adb: install Android SDK Platform-Tools. gh: winget install GitHub.cli, then gh auth login"
        }
    }
    # -CommandType Application: names are case-insensitive, and plain Get-Command adb would find the Adb function.
    $global:TvAdbExe = (Get-Command adb -CommandType Application | Select-Object -First 1).Source
    $global:TvSerial = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }

    Write-Host 'Starting ADB...'
    $r = Invoke-AdbRaw -AdbArgs @('start-server') -TimeoutSec 30
    if ($r.Code -ne 0) { throw "adb start-server failed: $($r.Out)" }
    Write-Host "Connecting to $global:TvSerial..."
    $connect = (Invoke-AdbRaw -AdbArgs @('connect', $global:TvSerial) -TimeoutSec 30).Out
    if ("$connect" -notmatch 'connected') {
        throw "adb connect $global:TvSerial failed: $connect (check the TV is on and Network debugging is enabled)"
    }
    $state = (Invoke-AdbRaw -AdbArgs @('-s', $global:TvSerial, 'get-state') -TimeoutSec 15).Out.Trim()
    if ($state -ne 'device') {
        throw "TV is '$state', not ready. If it says unauthorized, accept the debugging prompt on the TV and run again."
    }
}

function Invoke-AdbRaw {
    # Runs adb.exe and returns @{ Code; Out } once adb itself exits, or throws after $TimeoutSec.
    # Why not & adb ... 2>&1: when adb starts its background server, the server inherits the output pipe and keeps
    # it open, so PowerShell waits for the pipe forever. Here we wait for adb's exit, then read what it wrote.
    param([string[]]$AdbArgs, [int]$TimeoutSec = 60)
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = $global:TvAdbExe
    $psi.Arguments = ($AdbArgs | ForEach-Object { if ($_ -match '[\s"]') { '"' + ($_ -replace '"', '\"') + '"' } else { $_ } }) -join ' '
    $psi.UseShellExecute = $false
    $psi.CreateNoWindow = $true
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $p = [System.Diagnostics.Process]::Start($psi)
    $stdout = $p.StandardOutput.ReadToEndAsync()
    $stderr = $p.StandardError.ReadToEndAsync()
    if (-not $p.WaitForExit($TimeoutSec * 1000)) {
        try { $p.Kill() } catch { }
        throw "adb $($psi.Arguments) did not finish within $TimeoutSec s"
    }
    # Give the readers a moment; a server adb just started may hold the pipes open, so don't wait for them to close.
    [void][System.Threading.Tasks.Task]::WaitAll(@($stdout, $stderr), 3000)
    $text = ''
    if ($stdout.IsCompleted) { $text += $stdout.Result }
    if ($stderr.IsCompleted) { $text += $stderr.Result }
    return @{ Code = $p.ExitCode; Out = $text.TrimEnd() }
}

function Adb {
    # adb against the TV; returns its output lines, throws on failure unless -Quiet is among the arguments.
    # A plain function (no param block), so adb's own -d / -s / -p flags pass through untouched.
    $quiet = $args -contains '-Quiet'
    $adbArgs = @($args | Where-Object { $_ -ne '-Quiet' } | ForEach-Object { "$_" })
    $timeout = if ($adbArgs[0] -eq 'install') { 300 } else { 60 }
    $r = Invoke-AdbRaw -AdbArgs (@('-s', $global:TvSerial) + $adbArgs) -TimeoutSec $timeout
    $global:LASTEXITCODE = $r.Code
    if ($r.Code -ne 0 -and -not $quiet) { throw "adb $($adbArgs -join ' ') failed: $($r.Out)" }
    return ($r.Out -split "`r?`n")
}

function Get-LatestApk {
    # Downloads the newest build's APK; returns its path. From the rolling GitHub Release $Release when there is one
    # (V2 release publishes there; release files don't count against the Actions storage quota), else from the
    # $Artifact artifact of the newest successful run of $Workflow on $Branch.
    param([string]$Workflow, [string]$Artifact, [string]$Branch, [string]$Dir, [string]$Release = '')
    if (-not $global:TvRepo) { [void](Resolve-TvRepo) }
    gh auth status *> $null
    if ($LASTEXITCODE -ne 0) { throw 'gh is not signed in: run gh auth login' }
    if (Test-Path $Dir) { Remove-Item -Recurse -Force $Dir }
    if ($Release -and $Branch -eq 'main') {
        $rel = gh release view $Release --repo $global:TvRepo --json name,body,publishedAt 2> $null | ConvertFrom-Json
        if ($rel) {
            Write-Host "Build: $($rel.body.Split([char]10)[0]) ($($rel.publishedAt))"
            gh release download $Release --repo $global:TvRepo --pattern '*.apk' --dir $Dir | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "downloading the APK from release $Release failed" }
            $apk = (Get-ChildItem $Dir -Recurse -Filter *.apk | Select-Object -First 1).FullName
            if (-not $apk) { throw "release $Release has no APK" }
            return $apk
        }
        Write-Host "No $Release release yet; trying the newest $Workflow artifact."
    }
    $run = gh run list --repo $global:TvRepo --workflow $Workflow --branch $Branch --status success --limit 1 `
        --json databaseId,headSha,displayTitle,createdAt | ConvertFrom-Json
    if (-not $run) { throw "no successful $Workflow run on branch $Branch yet; check the Actions tab" }
    $run = $run[0]
    Write-Host "Build: run $($run.databaseId), commit $($run.headSha.Substring(0, 7)) '$($run.displayTitle)' ($($run.createdAt))"
    gh run download $run.databaseId --repo $global:TvRepo --name $Artifact --dir $Dir | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "downloading the $Artifact artifact failed" }
    $apk = (Get-ChildItem $Dir -Recurse -Filter *.apk | Select-Object -First 1).FullName
    if (-not $apk) { throw "the $Artifact artifact has no APK" }
    return $apk
}

function Get-InstalledVersion {
    # "versionName (build N)" of a package on the TV, or $null when it is not installed.
    param([string]$Package)
    $info = (Adb shell dumpsys package $Package -Quiet) -join "`n"
    if ($info -notmatch 'versionCode=(\d+)') { return $null }
    $code = $Matches[1]
    $name = if ($info -match 'versionName=(\S+)') { $Matches[1] } else { '?' }
    return "$name (build $code)"
}
