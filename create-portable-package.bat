@echo off
REM Create a portable WhisperCat package (JAR + launcher script)
REM This creates a simple ZIP that users can extract and run

setlocal

echo Creating portable WhisperCat package...
echo.

REM Build if needed
if not exist "target\Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar" (
    echo Building JAR first...
    call mvn clean package
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Build failed!
        exit /b 1
    )
)

REM Create portable directory
set PORTABLE_DIR=portable-whispercat
if exist "%PORTABLE_DIR%" rmdir /s /q "%PORTABLE_DIR%"
mkdir "%PORTABLE_DIR%"

REM Copy JAR
echo Copying JAR file...
copy "target\Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar" "%PORTABLE_DIR%\WhisperCat.jar"

REM Create launcher script in portable directory
echo Creating launcher script...
(
echo @echo off
echo REM WhisperCat Portable Launcher
echo.
echo java -jar WhisperCat.jar
echo.
echo if %%ERRORLEVEL%% NEQ 0 ^(
echo     echo ERROR: Failed to launch WhisperCat!
echo     echo Make sure Java 11+ is installed.
echo     pause
echo ^)
) > "%PORTABLE_DIR%\WhisperCat.bat"

REM Copy README
echo Creating README...
(
echo WhisperCat Portable v1.6.0-xblabs
echo ===================================
echo.
echo Requirements:
echo - Java 11 or higher
echo.
echo To run:
echo 1. Double-click WhisperCat.bat
echo    OR
echo 2. Run from command line: java -jar WhisperCat.jar
echo.
echo Settings are saved to:
echo %%APPDATA%%\WhisperCat\config.properties
echo.
echo Your settings persist between runs!
echo.
echo For more info: https://github.com/xblabs/whispercat-xb
) > "%PORTABLE_DIR%\README.txt"

REM Create ZIP (requires PowerShell)
echo Creating ZIP archive...
powershell -command "Compress-Archive -Path '%PORTABLE_DIR%\*' -DestinationPath 'WhisperCat-Portable.zip' -Force"

echo.
echo ========================================
echo SUCCESS! Portable package created:
echo WhisperCat-Portable.zip
echo.
echo Contents:
echo - WhisperCat.jar
echo - WhisperCat.bat (launcher)
echo - README.txt
echo ========================================
pause
