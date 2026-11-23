# One-time setup for V2 release signing (Documents/Requirements.md section 2): makes the release keystore on this PC
# and stores it in the repository's GitHub Secrets, where the "V2 release" workflow reads it. Every build is then
# signed with the same key, so new builds install over old ones with adb install -r.
#
# Needs: the GitHub CLI signed in (gh auth login), and keytool from a JDK 17+ (Android Studio's jbr has one).
# Usage: Scripts\Setup-Signing.ps1 [-Repo <owner>/<name>] [-KeystorePath <file>] [-Force]
#   -Repo   the GitHub repository to store the key in; by default the one this clone came from
#
# A keystore already at -KeystorePath (with its .password.txt) is reused when the repository has no key yet: that is
# how a new copy of the repository keeps signing with your existing key, so builds still install over the V2 on the TV.
#
# The keystore and its password stay on this PC in the keystore's folder. BACK BOTH UP (for example in a password
# manager). If they are lost, a new key means uninstalling V2 from the TV once, which wipes its settings.
param(
    [string]$Repo = '',
    [string]$KeystorePath = (Join-Path $env:USERPROFILE '.android\tvhome-v2-release.jks'),
    [switch]$Force
)
# Continue, not Stop: Windows PowerShell 5.1 turns native stderr output into terminating errors under Stop.
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot 'TvAdb.ps1')
$repo = Resolve-TvRepo -Repo $Repo
$alias = 'tvhome-v2'
Write-Host "Repository: $repo"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw 'gh is not on PATH: winget install GitHub.cli, then gh auth login' }
gh auth status *> $null
if ($LASTEXITCODE -ne 0) { throw 'gh is not signed in: run gh auth login' }

# keytool: JAVA_HOME, then Android Studio's bundled JDK, then PATH
$keytool = $null
if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\keytool.exe")) { $keytool = "$env:JAVA_HOME\bin\keytool.exe" }
if (-not $keytool) {
    $keytool = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'jbr\bin\keytool.exe' } | Where-Object { Test-Path $_ } | Select-Object -First 1
}
if (-not $keytool) { $keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source }
if (-not $keytool) { throw 'keytool not found: set JAVA_HOME to a JDK 17+ (Android Studio ships one in its jbr folder)' }

$existing = gh secret list --repo $repo 2>$null | Out-String
if ($existing -match 'V2_KEYSTORE_BASE64' -and -not $Force) {
    throw 'The repository already has a V2 signing key in its secrets. Replacing it means uninstalling V2 from the TV once. Run again with -Force only if you mean to.'
}
if (Test-Path $KeystorePath) { Write-Host "Reusing the existing keystore $KeystorePath" }

$passwordFile = [System.IO.Path]::ChangeExtension($KeystorePath, '.password.txt')
if (-not (Test-Path $KeystorePath)) {
    New-Item -ItemType Directory -Force (Split-Path $KeystorePath) | Out-Null
    # 32 random letters and digits. PKCS12 keystores use one password for the store and the key.
    $chars = [char[]]'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789'
    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $password = -join ($bytes | ForEach-Object { $chars[$_ % $chars.Length] })
    & $keytool -genkeypair -keystore $KeystorePath -storetype PKCS12 -alias $alias -keyalg RSA -keysize 4096 `
        -validity 36500 -storepass $password -keypass $password -dname 'CN=TV Home V2' 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $KeystorePath)) { throw 'keytool could not create the keystore' }
    "TV Home V2 release keystore`r`nFile: $KeystorePath`r`nAlias: $alias`r`nPassword: $password`r`n" |
        Out-File -Encoding ascii $passwordFile
    Write-Host "Created $KeystorePath"
} else {
    if (-not (Test-Path $passwordFile)) { throw "No password file next to $KeystorePath ($passwordFile)" }
    $password = ((Get-Content $passwordFile) -match '^Password: ' | Select-Object -First 1) -replace '^Password: ', ''
    if (-not $password) { throw "No 'Password:' line in $passwordFile" }
}

$base64 = [Convert]::ToBase64String([System.IO.File]::ReadAllBytes($KeystorePath))
foreach ($secret in @(
        @('V2_KEYSTORE_BASE64', $base64),
        @('V2_KEYSTORE_PASSWORD', $password),
        @('V2_KEY_ALIAS', $alias),
        @('V2_KEY_PASSWORD', $password))) {
    # --body, not stdin: Windows PowerShell adds a line break to piped text, which would corrupt the password.
    gh secret set $secret[0] --repo $repo --body $secret[1] | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "gh secret set $($secret[0]) failed" }
}
Write-Host ''
Write-Host "Stored the signing key in the GitHub Secrets of $repo."
Write-Host "Keystore: $KeystorePath"
Write-Host "Password: $passwordFile"
Write-Host 'Back up both files now (password manager or another drive). Then run the V2 release workflow on GitHub.'
