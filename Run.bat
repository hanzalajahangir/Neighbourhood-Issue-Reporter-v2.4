@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo Java JDK was not found on PATH.
    echo Install a JDK 8 or newer, then try again.
    pause
    exit /b 1
)

where javaw >nul 2>nul
if errorlevel 1 (
    echo javaw was not found on PATH.
    echo Install a full JDK 8 or newer, then try again.
    pause
    exit /b 1
)

set "BUILD_DIR=%~dp0out"
set "SOURCE_LIST=%TEMP%\neighbourhood-issue-reporter-sources.txt"

if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"

> "%SOURCE_LIST%" (
    for /r "%~dp0src" %%F in (*.java) do (
        set "SOURCE_FILE=%%F"
        echo "!SOURCE_FILE:\=/!"
    )
)

javac -encoding UTF-8 -d "%BUILD_DIR%" @"%SOURCE_LIST%"
if errorlevel 1 (
    del "%SOURCE_LIST%" >nul 2>nul
    echo.
    echo Build failed. Fix the compiler errors above, then run this file again.
    pause
    exit /b 1
)
del "%SOURCE_LIST%" >nul 2>nul

start "" javaw -cp "%BUILD_DIR%" Main
exit /b 0