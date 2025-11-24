# WhisperCat Portable EXE Creator (PowerShell)
# Creates a taskbar-friendly .exe launcher using Launch4j

param(
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"

Write-Host "WhisperCat Portable EXE Builder" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Build JAR if needed
if (-not $SkipBuild) {
    if (-not (Test-Path "target\Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar")) {
        Write-Host "[1/3] Building JAR with Maven..." -ForegroundColor Yellow
        & mvn clean package
        if ($LASTEXITCODE -ne 0) {
            Write-Host "✗ Maven build failed!" -ForegroundColor Red
            exit 1
        }
        Write-Host "✓ Build successful" -ForegroundColor Green
        Write-Host ""
    } else {
        Write-Host "[1/3] Using existing JAR" -ForegroundColor Yellow
        Write-Host ""
    }
} else {
    Write-Host "[1/3] Skipping build (using existing JAR)" -ForegroundColor Yellow
    Write-Host ""
}

# Step 2: Get Launch4j
Write-Host "[2/3] Checking for Launch4j..." -ForegroundColor Yellow

$launch4jExe = $null

# Check system PATH
$launch4jCmd = Get-Command launch4jc -ErrorAction SilentlyContinue
if ($launch4jCmd) {
    $launch4jExe = $launch4jCmd.Source
    Write-Host "✓ Found Launch4j in PATH: $launch4jExe" -ForegroundColor Green
} elseif (Test-Path "launch4j\launch4jc.exe") {
    $launch4jExe = "launch4j\launch4jc.exe"
    Write-Host "✓ Found Launch4j locally: $launch4jExe" -ForegroundColor Green
} else {
    Write-Host "✗ Launch4j not found. Downloading..." -ForegroundColor Yellow

    try {
        Write-Host "  Downloading from SourceForge..." -ForegroundColor Gray
        Invoke-WebRequest -Uri 'https://sourceforge.net/projects/launch4j/files/launch4j-3/3.14/launch4j-3.14-win32.zip/download' -OutFile 'launch4j.zip'

        Write-Host "  Extracting..." -ForegroundColor Gray
        Expand-Archive -Path 'launch4j.zip' -DestinationPath '.' -Force
        Remove-Item 'launch4j.zip'

        $launch4jExe = "launch4j\launch4jc.exe"
        Write-Host "✓ Launch4j downloaded and extracted" -ForegroundColor Green
    } catch {
        Write-Host "✗ ERROR: Failed to download Launch4j!" -ForegroundColor Red
        Write-Host ""
        Write-Host "Please download manually from:" -ForegroundColor Yellow
        Write-Host "https://sourceforge.net/projects/launch4j/" -ForegroundColor Cyan
        exit 1
    }
}

Write-Host ""

# Step 3: Create EXE
Write-Host "[3/3] Creating WhisperCat.exe with Launch4j..." -ForegroundColor Yellow

& $launch4jExe launch4j-config.xml

if ($LASTEXITCODE -ne 0) {
    Write-Host "✗ Launch4j failed!" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path "WhisperCat.exe")) {
    Write-Host "✗ WhisperCat.exe was not created!" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "======================================" -ForegroundColor Green
Write-Host "SUCCESS! WhisperCat.exe created!" -ForegroundColor Green
Write-Host "======================================" -ForegroundColor Green
Write-Host ""
Write-Host "📌 To pin to taskbar:" -ForegroundColor White
Write-Host "   1. Right-click WhisperCat.exe" -ForegroundColor Cyan
Write-Host "   2. Select 'Pin to taskbar'" -ForegroundColor Cyan
Write-Host ""
Write-Host "📋 Requirements:" -ForegroundColor White
Write-Host "   - Java 11+ must be installed" -ForegroundColor Gray
Write-Host "   - Download from: https://adoptium.net/" -ForegroundColor Gray
Write-Host ""
Write-Host "💾 Settings location:" -ForegroundColor White
Write-Host "   %APPDATA%\WhisperCat\config.properties" -ForegroundColor Cyan
