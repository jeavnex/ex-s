param(
    [string]$Keytool = 'C:\Program Files\Java\jdk1.8.0_231\bin\keytool.exe'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$signingDirectory = Join-Path $projectRoot '.local-signing'
$privateDirectory = Join-Path $projectRoot 'private'
$keystorePath = Join-Path $signingDirectory 'release.jks'
$secretsPath = Join-Path $privateDirectory 'github-secrets.txt'

if (Test-Path -LiteralPath $keystorePath) {
    throw "Signing key already exists: $keystorePath"
}

if (-not (Test-Path -LiteralPath $Keytool)) {
    throw "keytool was not found: $Keytool"
}

function New-RandomSecret([int]$Length = 40) {
    $alphabet = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'
    $bytes = New-Object byte[] $Length
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($bytes)
    } finally {
        $rng.Dispose()
    }
    -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
}

New-Item -ItemType Directory -Force -Path $signingDirectory, $privateDirectory | Out-Null

$storePassword = New-RandomSecret
$keyPassword = New-RandomSecret
$alias = 'ex-s-release'

& $Keytool `
    -genkeypair `
    -v `
    -keystore $keystorePath `
    -storetype JKS `
    -storepass $storePassword `
    -keypass $keyPassword `
    -alias $alias `
    -keyalg RSA `
    -keysize 4096 `
    -validity 10000 `
    -dname 'CN=EX-S, OU=Personal, O=jeavnex, C=DE'

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed with exit code $LASTEXITCODE"
}

$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))
$secretLines = @(
    '# EX-S GitHub Actions secrets'
    '# Keep this file private. Never commit or send it in chat.'
    ''
    'EXS_KEYSTORE_BASE64='
    $keystoreBase64
    ''
    'EXS_STORE_PASSWORD='
    $storePassword
    ''
    'EXS_KEY_ALIAS='
    $alias
    ''
    'EXS_KEY_PASSWORD='
    $keyPassword
)

[IO.File]::WriteAllLines($secretsPath, $secretLines, [Text.UTF8Encoding]::new($false))

Write-Output "Signing key created: $keystorePath"
Write-Output "GitHub secret values created: $secretsPath"
