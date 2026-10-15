@echo off
setlocal EnableExtensions

set "ROOT=%~dp0"
cd /d "%ROOT%" || (
    echo [ERROR] Cannot open the project directory:
    echo         %ROOT%
    goto :failed
)

set "APP_HOST=127.0.0.1"
set "APP_PORT=8765"
set "PYTHON_EXE=D:\Work_Software\Anaconda3\envs\nlp_env\python.exe"
set "NODE_HOME=D:\Work_Software\nodejs"
set "NPM_CMD="
set "PYTHONUTF8=1"

title SmartSpeech Interview System
echo.
echo ============================================================
echo        SmartSpeech Interview System - One Click Start
echo ============================================================
echo.

if not exist "%PYTHON_EXE%" (
    echo [ERROR] Python was not found:
    echo         %PYTHON_EXE%
    echo         Create the nlp_env environment and install requirements.txt.
    goto :failed
)

if exist "%NODE_HOME%\npm.cmd" (
    set "NPM_CMD=%NODE_HOME%\npm.cmd"
    set "PATH=%NODE_HOME%;%PATH%"
)

if not defined NPM_CMD (
    for /f "delims=" %%I in ('where npm 2^>nul') do (
        set "NPM_CMD=%%I"
        goto :npm_found
    )
)

:npm_found
if not defined NPM_CMD (
    echo [ERROR] npm was not found. Install Node.js 20 or newer.
    goto :failed
)

if not exist ".env" (
    if exist ".env.example" (
        copy /y ".env.example" ".env" >nul
        if errorlevel 1 (
            echo [ERROR] Failed to create .env from .env.example.
            goto :failed
        )
        echo [INFO] Created .env. Add DEEPSEEK_API_KEY for full features.
    ) else (
        echo [ERROR] Both .env and .env.example are missing.
        goto :failed
    )
)

for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
    if /I "%%A"=="APP_HOST" set "APP_HOST=%%~B"
    if /I "%%A"=="APP_PORT" set "APP_PORT=%%~B"
)

if not defined APP_HOST set "APP_HOST=127.0.0.1"
if not defined APP_PORT set "APP_PORT=8765"
echo(%APP_PORT%| findstr /R "^[0-9][0-9]*$" >nul
if errorlevel 1 (
    echo [ERROR] APP_PORT must be a number. Current value: %APP_PORT%
    goto :failed
)

set "APP_URL=http://%APP_HOST%:%APP_PORT%"

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "try { $response = Invoke-WebRequest -UseBasicParsing -Uri '%APP_URL%/api/health' -TimeoutSec 2; if ($response.StatusCode -eq 200) { exit 0 } else { exit 1 } } catch { exit 1 }"
if not errorlevel 1 (
    echo [INFO] The system is already running. Opening the browser.
    start "" "%APP_URL%"
    exit /b 0
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$listener = Get-NetTCPConnection -State Listen -LocalPort %APP_PORT% -ErrorAction SilentlyContinue; if ($null -ne $listener) { exit 0 } else { exit 1 }"
if not errorlevel 1 (
    echo [ERROR] Port %APP_PORT% is already used by another program.
    echo         Close it or change APP_PORT in .env.
    goto :failed
)

echo [1/4] Checking Python dependencies...
"%PYTHON_EXE%" -c "import flask, waitress, cv2, mediapipe, whisper, edge_tts, docx, pypdf, win32com" >nul 2>nul
if errorlevel 1 (
    echo       Installing missing Python dependencies. This may take a while...
    "%PYTHON_EXE%" -m pip install -r requirements.txt
    if errorlevel 1 (
        echo [ERROR] Python dependency installation failed.
        goto :failed
    )
)
echo       Done.

echo [2/4] Checking frontend dependencies...
pushd "frontend"
if errorlevel 1 (
    echo [ERROR] Cannot open the frontend directory.
    goto :failed
)

if not exist "node_modules\.bin\vite.cmd" (
    echo       node_modules is missing. Running npm ci...
    call "%NPM_CMD%" ci --no-audit --no-fund
    if errorlevel 1 (
        popd
        echo [ERROR] npm ci failed.
        goto :failed
    )
)
echo       Done.

echo [3/4] Building the Vue frontend...
call "%NPM_CMD%" run build
if errorlevel 1 (
    popd
    echo [ERROR] Frontend build failed.
    goto :failed
)
popd
echo       Done.

echo [4/4] Starting Flask and Waitress...
echo       URL: %APP_URL%
echo       Keep this window open. Close it to stop the system.
echo.

start "" /B powershell.exe -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command "$url='%APP_URL%'; for ($i = 0; $i -lt 90; $i++) { try { $response = Invoke-WebRequest -UseBasicParsing -Uri ($url + '/api/health') -TimeoutSec 2; if ($response.StatusCode -eq 200) { Start-Process $url; exit 0 } } catch {}; Start-Sleep -Milliseconds 700 }"

"%PYTHON_EXE%" -m backend.run
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] The system exited unexpectedly. Exit code: %EXIT_CODE%
    goto :failed
)

echo.
echo The system has stopped.
exit /b 0

:failed
echo.
echo Startup failed. Review the message above, then press any key to close.
pause >nul
exit /b 1
