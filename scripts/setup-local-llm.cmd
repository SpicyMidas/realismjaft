@echo off
rem Runs setup-local-llm.ps1 without changing the machine's execution policy. Arguments pass through.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup-local-llm.ps1" %*
exit /b %ERRORLEVEL%
