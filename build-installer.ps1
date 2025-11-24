# WhisperCat Windows Installer Build Script (PowerShell)
# Requires JDK 17+ with jpackage

param(
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"

Write-Host "WhisperCat Windows Installer Builder" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan
Write-Host ""

# Check Java version
try {
    $javaVersion = java -version 2>&1 | Select-String "version" | ForEach-Object { $_.ToString() }
    Write-Host "✓ Java found: $javaVersion" -ForegroundColor Green
} catch {
    Write-Host "✗ ERROR: Java not found in PATH!" -ForegroundColor Red
    Write-Host "  Please install JDK 17+ from https://adoptium.net/" -ForegroundColor Yellow
    exit 1
}

# Check jpackage availability
try {
    $null = jpackage --version 2>&1
    Write-Host "✓ jpackage found" -ForegroundColor Green
} catch {
    Write-Host "✗ ERROR: jpackage not found!" -ForegroundColor Red
    Write-Host "  jpackage requires JDK 16+ (you may have a JRE installed)" -ForegroundColor Yellow
    exit 1
}

Write-Host ""

# Step 1: Build with Maven
if (-not $SkipBuild) {
    Write-Host "[1/2] Building JAR with Maven..." -ForegroundColor Yellow
    & mvn clean package
    if ($LASTEXITCODE -ne 0) {
        Write-Host "✗ Maven build failed!" -ForegroundColor Red
        exit 1
    }
    Write-Host "✓ Build successful" -ForegroundColor Green
    Write-Host ""
} else {
    Write-Host "[1/2] Skipping Maven build (using existing JAR)" -ForegroundColor Yellow
    Write-Host ""
}

# Verify JAR exists
$jarPath = "target\Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar"
if (-not (Test-Path $jarPath)) {
    Write-Host "✗ ERROR: JAR file not found at $jarPath" -ForegroundColor Red
    exit 1
}

# Step 2: Create installer with jpackage
Write-Host "[2/2] Creating Windows installer with jpackage..." -ForegroundColor Yellow

# Clean output directory
if (Test-Path "installer-output") {
    Remove-Item -Recurse -Force "installer-output"
}

# Run jpackage
& jpackage `
    --type exe `
    --input target `
    --name WhisperCat `
    --main-jar Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar `
    --main-class org.whispercat.AudioRecorderUI `
    --icon src/main/resources/whispercat.ico `
    --app-version 1.6.0 `
    --vendor "xblabs" `
    --copyright "MIT License" `
    --description "Audio transcription tool with Whisper API integration" `
    --win-dir-chooser `
    --win-menu `
    --win-shortcut `
    --dest installer-output

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ jpackage failed!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "SUCCESS! Installer created at:" -ForegroundColor Green
Write-Host "installer-output\WhisperCat-1.6.0.exe" -ForegroundColor Cyan
Write-Host "======================================" -ForegroundColor Green
Write-Host ""
Write-Host "You can now distribute this installer to users." -ForegroundColor White
Write-Host "It will install WhisperCat with Start Menu shortcuts." -ForegroundColor White
Write-Host ""
Write-Host "Settings are automatically saved to:" -ForegroundColor White
Write-Host "%APPDATA%\WhisperCat\config.properties" -ForegroundColor Cyan
