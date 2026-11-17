[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-zA-Z0-9_-]+$')][string]$Name,
    [ValidateRange(5,300)][int]$Seconds=45,
    [ValidateSet('playback','output')][string]$Source='playback',
    [string]$Serial='',
    [string]$Notes='',
    [string]$Adb=(Join-Path $env:LOCALAPPDATA 'Android\platform-tools\adb.exe'),
    [string]$Scrcpy=(Join-Path $PSScriptRoot 'scrcpy\scrcpy.exe'),
    [string]$Directory=(Join-Path $PSScriptRoot 'recordings')
)
$ErrorActionPreference='Stop'
if (!(Test-Path $Adb)) { throw "ADB not found: $Adb" }
if (!(Test-Path $Scrcpy)) { throw "scrcpy not found: $Scrcpy" }
# This is deliberately the first device interaction, on every invocation.
$devices = & $Adb devices -l
if ($LASTEXITCODE -ne 0) { throw 'Could not check ADB devices' }
$available=@($devices | ForEach-Object {
    if ($_ -match '^(\S+)\s+device(?:\s|$)') { $Matches[1] }
})
if ($Serial) {
    if ($available -notcontains $Serial) { throw 'Requested device is absent or unauthorized' }
} elseif ($available.Count -eq 1) {
    $Serial=$available[0]
} else { throw 'Connect exactly one authorized phone, or specify -Serial' }
New-Item -ItemType Directory -Force $Directory | Out-Null
$wav=Join-Path $Directory ($Name+'.wav')
$metadata=Join-Path $Directory ($Name+'.json')
$log=Join-Path $Directory ($Name+'.log')
foreach ($path in @($wav,$metadata,$log)) {
    if (Test-Path $path) { throw "Refusing to overwrite: $path" }
}
$scrcpyArgs=@('--serial',$Serial,'--no-video','--no-control','--no-playback',
    '--require-audio',('--audio-source='+$Source),'--audio-codec=raw',
    ('--record='+$wav),('--time-limit='+$Seconds))
if ($Source -eq 'playback') { $scrcpyArgs+='--audio-dup' }
$info=[ordered]@{
    name=$Name; recorded_at_utc=[DateTime]::UtcNow.ToString('o');
    seconds_requested=$Seconds; source=$Source; codec='raw PCM 16-bit LE';
    notes=$Notes; command_arguments=$scrcpyArgs; status='started';
    scrcpy_version=(& $Scrcpy --version 2>&1 | Out-String).Trim()
}
$info | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $metadata
Write-Host "Recording $Name for $Seconds seconds. Start the SAME track from the beginning."
if ($Source -eq 'output') {
    Write-Host 'OUTPUT mode changes audio routing and may silence phone playback. Do not mix modes in a comparison.'
}
$previousAdb=$env:ADB
$env:ADB=$Adb
try {
    # Native stderr is logging, not a PowerShell terminating error in Windows PS 5.1.
    $ErrorActionPreference='Continue'
    & $Scrcpy @scrcpyArgs 2>&1 | Tee-Object -FilePath $log | Out-Host
    $exitCode=$LASTEXITCODE
    $ErrorActionPreference='Stop'
    if ($exitCode -ne 0 -or !(Test-Path $wav) -or (Get-Item $wav).Length -lt 1024) {
        throw 'Recording failed; inspect the log. No settings were changed by this script.'
    }
    $info.status='completed'
    $info.sha256=(Get-FileHash -Algorithm SHA256 $wav).Hash.ToLowerInvariant()
} catch {
    $info.status='failed';$info.error=$_.Exception.Message
    throw
} finally {
    $env:ADB=$previousAdb
    $info | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 $metadata
}
Write-Host "Saved: $wav"
