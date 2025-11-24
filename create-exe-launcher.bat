@echo off
REM Create WhisperCat.exe launcher using Launch4j
REM This creates a proper .exe that can be pinned to taskbar

setlocal

echo WhisperCat EXE Launcher Builder
echo ================================
echo.

REM Check if JAR exists
if not exist "target\Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar" (
    echo Building JAR first...
    call mvn clean package
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Maven build failed!
        pause
        exit /b 1
    )
    echo.
)

REM Check if Launch4j is installed
where launch4jc >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo Using system Launch4j...
    launch4jc launch4j-config.xml
    goto :check_result
)

REM Check if Launch4j is in current directory
if exist "launch4j\launch4jc.exe" (
    echo Using local Launch4j...
    launch4j\launch4jc.exe launch4j-config.xml
    goto :check_result
)

REM Download Launch4j if needed
echo Launch4j not found. Downloading...
echo.
powershell -Command "& {Invoke-WebRequest -Uri 'https://sourceforge.net/projects/launch4j/files/launch4j-3/3.14/launch4j-3.14-win32.zip/download' -OutFile 'launch4j.zip'}"
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to download Launch4j!
    echo.
    echo Please download manually from:
    echo https://sourceforge.net/projects/launch4j/
    echo.
    echo Or install Launch4j and add to PATH
    pause
    exit /b 1
)

echo Extracting Launch4j...
powershell -Command "Expand-Archive -Path 'launch4j.zip' -DestinationPath '.' -Force"
del launch4j.zip

echo Running Launch4j...
launch4j\launch4jc.exe launch4j-config.xml

:check_result
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Failed to create EXE!
    pause
    exit /b 1
)

if not exist "WhisperCat.exe" (
    echo ERROR: WhisperCat.exe was not created!
    pause
    exit /b 1
)

echo.
echo ========================================
echo SUCCESS! WhisperCat.exe created!
echo ========================================
echo.
echo You can now:
echo 1. Run WhisperCat.exe directly
echo 2. Pin it to your taskbar (right-click ^> Pin to taskbar)
echo 3. Create a desktop shortcut
echo.
echo NOTE: You need Java 11+ installed on your system.
echo Download from: https://adoptium.net/
echo.
pause
