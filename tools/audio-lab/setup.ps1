[CmdletBinding()]
param([string]$Archive=(Join-Path $PSScriptRoot 'scrcpy-win64-v5.0.1.zip'))
$ErrorActionPreference='Stop'
$expected='b12a2c4ee8be317422451fc7dcf8ee20a71b5ea7ef9ad73ddd825a25316227a5'
if (!(Test-Path $Archive)) { throw "Official scrcpy archive is missing: $Archive" }
if ((Get-FileHash -Algorithm SHA256 $Archive).Hash.ToLowerInvariant() -ne $expected) {
    throw 'scrcpy archive checksum mismatch'
}
$destination=Join-Path $PSScriptRoot 'scrcpy'
if (!(Test-Path $destination)) {
    $temporary=Join-Path $PSScriptRoot ('.unpack-'+[Guid]::NewGuid().ToString('N'))
    try {
        Expand-Archive -LiteralPath $Archive -DestinationPath $temporary
        $exe=Get-ChildItem $temporary -Recurse -Filter scrcpy.exe | Select-Object -First 1
        if (!$exe) { throw 'scrcpy executable not found in archive' }
        Copy-Item -LiteralPath $exe.Directory.FullName -Destination $destination -Recurse
    } finally {
        if (Test-Path $temporary) { Remove-Item -LiteralPath $temporary -Recurse -Force }
    }
}
$version=(& (Join-Path $destination 'scrcpy.exe') --version 2>&1 | Out-String)
if ($LASTEXITCODE -ne 0 -or $version -notmatch 'scrcpy 5\.0\.1') { throw 'Unexpected scrcpy version' }
Write-Host $version
Write-Host 'Ready. Setup did not connect to the phone or start any recording.'
