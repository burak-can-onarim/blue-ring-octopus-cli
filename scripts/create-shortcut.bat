@echo off
rem Creates a desktop shortcut (with the application icon) that starts start-agent.bat.
rem Usage: scripts\create-shortcut.bat [output folder]
if "%~1"=="" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0create-shortcut.ps1"
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0create-shortcut.ps1" -OutputDir "%~1"
)
if errorlevel 1 (
    echo.
    echo Could not create the shortcut.
    exit /b 1
)
