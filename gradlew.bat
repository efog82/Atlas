@echo off
where gradle >nul 2>nul
if %errorlevel% equ 0 (
  gradle %*
  exit /b %errorlevel%
)
echo Executa scripts\bootstrap-gradle.ps1 no PowerShell ou instala Gradle 8.11.1.
exit /b 1
