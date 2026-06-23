@echo off
setlocal enabledelayedexpansion

set ROOT=%~dp0

rem settlement-service is optional. Pass it explicitly if needed.
rem Full deploy (no args) also starts nginx and sample-react.
if "%~1"=="" (
    set JAVA_SERVICES=user-service banking-service reward-service wallet-service transfer-service ledger-service api-gateway
    set FULL_DEPLOY=1
) else (
    set JAVA_SERVICES=%*
    set FULL_DEPLOY=0
)

echo [1/2] Building JARs...
for %%S in (%JAVA_SERVICES%) do (
    echo   ^> Building %%S...
    pushd "%ROOT%%%S"
    call gradlew.bat bootJar -x test --no-daemon -q
    if errorlevel 1 (
        echo   [ERROR] %%S build failed
        popd
        exit /b 1
    )
    popd
    echo   ^> %%S done
)

echo.
echo [2/2] Deploying with Docker Compose...
pushd "%ROOT%"
if "%FULL_DEPLOY%"=="1" (
    docker compose up -d --build %JAVA_SERVICES% sample-react nginx
) else (
    docker compose up -d --build %JAVA_SERVICES%
)
popd

echo.
echo Done!
pause
