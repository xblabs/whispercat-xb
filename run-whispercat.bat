@echo off
REM WhisperCat Launcher Script
REM Simple batch file to launch WhisperCat JAR

setlocal

REM Check if JAR exists
set JAR_FILE=target\Audiorecorder-1.0-SNAPSHOT-jar-with-dependencies.jar

if not exist "%JAR_FILE%" (
    echo ERROR: JAR file not found at %JAR_FILE%
    echo.
    echo Please build the project first:
    echo   mvn clean package
    echo.
    pause
    exit /b 1
)

REM Launch WhisperCat
echo Starting WhisperCat...
echo.
java -jar "%JAR_FILE%"

REM If Java fails
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Failed to launch WhisperCat!
    echo Make sure Java 11+ is installed and in your PATH.
    echo.
    pause
    exit /b 1
)
