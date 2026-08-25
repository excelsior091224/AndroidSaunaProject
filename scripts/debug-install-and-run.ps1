param(
    [string]$PhoneSerial = $env:PHONE_SERIAL,
    [string]$WatchSerial = $env:WATCH_SERIAL,
    [switch]$SkipBuild,
    [string]$WatchConnect = $env:WATCH_CONNECT_ONLY,
    [string]$WatchIp = $env:WATCH_IP,
    [string]$PairPort = $env:WATCH_PAIR_PORT,
    [string]$PairCode = $env:WATCH_PAIR_CODE,
    [string]$AdbPort = $(if ($env:WATCH_ADB_PORT) { $env:WATCH_ADB_PORT } else { "5555" }),
    [int]$RetryCount = $(if ($env:ADB_RETRY_COUNT) { [int]$env:ADB_RETRY_COUNT } else { 3 }),
    [int]$RetryDelaySec = $(if ($env:ADB_RETRY_DELAY_SEC) { [int]$env:ADB_RETRY_DELAY_SEC } else { 2 }),
    [string]$LogFile = $env:LOG_FILE
)

$ErrorActionPreference = "Stop"

# Build, install, and launch debug APKs for phone and Wear OS devices.
$RootDir = Resolve-Path (Join-Path $PSScriptRoot "..")
$MobileApk = Join-Path $RootDir "mobile/build/outputs/apk/debug/mobile-debug.apk"
$WearApk = Join-Path $RootDir "wear/build/outputs/apk/debug/wear-debug.apk"
$MobilePkg = "com.totonoi.sauna.mobile"
$WearPkg = "com.totonoi.sauna.wear"

function Invoke-WithRetry {
    param(
        [string]$Label,
        [scriptblock]$Action
    )

    for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
        Write-Host "[$Label] attempt $attempt/$RetryCount"
        try {
            & $Action
            return
        }
        catch {
            if ($attempt -ge $RetryCount) {
                throw "[$Label] failed after $RetryCount attempts. $($_.Exception.Message)"
            }
            Start-Sleep -Seconds $RetryDelaySec
        }
    }
}

function Setup-Logging {
    if (-not $LogFile) {
        $ts = Get-Date -Format "yyyyMMdd-HHmmss"
        $script:LogFile = Join-Path $RootDir "logs/debug-$ts.log"
    }

    $logDir = Split-Path -Parent $LogFile
    if (-not (Test-Path $logDir)) {
        New-Item -ItemType Directory -Path $logDir -Force | Out-Null
    }
    if (-not (Test-Path $LogFile)) {
        New-Item -ItemType File -Path $LogFile -Force | Out-Null
    }

    Start-Transcript -Path $LogFile -Append | Out-Null
    Write-Host "[log] Writing logs to: $LogFile"
}

function Finish-Logging {
    try {
        Stop-Transcript | Out-Null
    }
    catch {
    }
}

Setup-Logging

if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    throw "adb not found. Add Android platform-tools to PATH."
}

if ($WatchConnect) {
    Write-Host "[prep] Connecting to watch over Wi-Fi: $WatchConnect"
    Invoke-WithRetry -Label "watch-connect" -Action { adb connect $WatchConnect | Out-Null }
    $WatchSerial = $WatchConnect
}

if ($WatchIp) {
    if (-not $AdbPort) {
        $AdbPort = Read-Host "Enter watch adb port [5555]"
        if (-not $AdbPort) {
            $AdbPort = "5555"
        }
    }

    if (-not $PairPort -and -not $PairCode) {
        $pairChoice = Read-Host "Need adb pair first? [y/N]"
        if ($pairChoice -eq "y" -or $pairChoice -eq "Y") {
            $PairPort = Read-Host "Enter watch pair port"
            $PairCode = Read-Host "Enter watch pair code"
        }
    }

    if ($PairPort -or $PairCode) {
        if (-not $PairPort -or -not $PairCode) {
            throw "Both -PairPort and -PairCode are required when -WatchIp is used for pairing."
        }
        Write-Host "[prep] Pairing watch: $WatchIp`:$PairPort"
        Invoke-WithRetry -Label "watch-pair" -Action { adb pair "$WatchIp`:$PairPort" $PairCode | Out-Null }
    }

    Write-Host "[prep] Connecting watch adb endpoint: $WatchIp`:$AdbPort"
    Invoke-WithRetry -Label "watch-connect" -Action { adb connect "$WatchIp`:$AdbPort" | Out-Null }
    $WatchSerial = "$WatchIp`:$AdbPort"
}

if (-not $SkipBuild) {
    Write-Host "[build] Building debug APKs..."
    Push-Location $RootDir
    try {
        & ./gradlew.bat :mobile:assembleDebug :wear:assembleDebug
    }
    finally {
        Pop-Location
    }
}

if (-not (Test-Path $MobileApk)) {
    throw "Mobile APK not found: $MobileApk"
}
if (-not (Test-Path $WearApk)) {
    throw "Wear APK not found: $WearApk"
}

$devLines = adb devices -l | Select-Object -Skip 1 | Where-Object { $_ -match "\sdevice\b" }
if ($devLines.Count -eq 0) {
    throw "No adb devices in 'device' state."
}

if (-not $PhoneSerial -or -not $WatchSerial) {
    $watchCandidates = @()
    $phoneCandidates = @()

    foreach ($line in $devLines) {
        $serial = ($line -split "\s+")[0]
        if ($line -match "features:watch" -or $line -match "model:Pixel_Watch" -or $line -match "product:wear") {
            $watchCandidates += $serial
        }
        else {
            $phoneCandidates += $serial
        }
    }

    if (-not $PhoneSerial -and $phoneCandidates.Count -eq 1) {
        $PhoneSerial = $phoneCandidates[0]
    }
    if (-not $WatchSerial -and $watchCandidates.Count -eq 1) {
        $WatchSerial = $watchCandidates[0]
    }
}

if (-not $PhoneSerial -or -not $WatchSerial) {
    Write-Host "Could not auto-resolve both devices."
    Write-Host "Connected devices:"
    adb devices -l
    throw "Specify -PhoneSerial and -WatchSerial explicitly."
}

Write-Host "[target] Using phone: $PhoneSerial"
Write-Host "[target] Using watch: $WatchSerial"

Write-Host "[install] Installing mobile app..."
Invoke-WithRetry -Label "install-mobile" -Action { adb -s $PhoneSerial install -r $MobileApk }

Write-Host "[install] Installing wear app..."
Invoke-WithRetry -Label "install-wear" -Action { adb -s $WatchSerial install -r $WearApk }

Write-Host "[launch] Launching apps..."
Invoke-WithRetry -Label "launch-mobile" -Action { adb -s $PhoneSerial shell monkey -p $MobilePkg 1 | Out-Null }
Invoke-WithRetry -Label "launch-wear" -Action { adb -s $WatchSerial shell monkey -p $WearPkg 1 | Out-Null }

Write-Host "Done."
Write-Host "Phone logs: adb -s $PhoneSerial logcat"
Write-Host "Watch logs: adb -s $WatchSerial logcat"
Write-Host "Session log file: $LogFile"

Finish-Logging
