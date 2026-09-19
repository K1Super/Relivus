@echo off
rem ============================================================
rem Relivus deployment verification script (Windows)
rem Verifies main chain after deployment: health, connection,
rem schema, generation, masking, JOIN consistency.
rem Requires: curl.exe (Win10 17063+) and powershell.
rem Usage:
rem   scripts\verify-deploy.bat                 use TOKEN from .env
rem   set RELIVUS_BASE_URL=http://host:8080 && scripts\verify-deploy.bat
rem Target DB overrides:
rem   VERIFY_DB_HOST / VERIFY_DB_PORT / VERIFY_DB_NAME /
rem   VERIFY_DB_USER / VERIFY_DB_PASS / VERIFY_DB_TYPE
rem Creates a one-time connection "relivus-verify" (recreated if exists).
rem ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0.."

set "BASE_URL=%RELIVUS_BASE_URL%"
if "%BASE_URL%"=="" set "BASE_URL=http://127.0.0.1:8080"

rem ---- config source: env var > .env ----
set "TOKEN=%RELIVUS_TOKEN%"
if "%TOKEN%"=="" if exist .env (
    for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
        set "_k=%%a"
        set "_v=%%b"
        if /i "!_k!"=="RELIVUS_TOKEN" set "TOKEN=!_v!"
    )
)
if "%TOKEN%"=="" (
    echo [FAIL] RELIVUS_TOKEN not found. Set it in .env or use: set RELIVUS_TOKEN=xxx
    exit /b 1
)

if "%VERIFY_DB_HOST%"=="" set "VERIFY_DB_HOST=127.0.0.1"
if "%VERIFY_DB_PORT%"=="" set "VERIFY_DB_PORT=5432"
if "%VERIFY_DB_NAME%"=="" set "VERIFY_DB_NAME=relivus_demo"
if "%VERIFY_DB_USER%"=="" set "VERIFY_DB_USER=postgres"
if "%VERIFY_DB_PASS%"=="" set "VERIFY_DB_PASS=postgres"
if "%VERIFY_DB_TYPE%"=="" set "VERIFY_DB_TYPE=postgresql"
set "CONN_NAME=relivus-verify"

set "PASS_COUNT=0"
set "FAIL_COUNT=0"

rem ---------- 1. health check ----------
curl -sf "%BASE_URL%/actuator/health" | findstr /c:""UP"" > nul
if %errorlevel%==0 ( echo [PASS] Health /actuator/health UP & set /a PASS_COUNT+=1
) else ( echo [FAIL] Health check failed & set /a FAIL_COUNT+=1 )

rem ---------- 2. connections ----------
set "AUTH=Authorization: Bearer %TOKEN%"
set "CT=Content-Type: application/json"

for /f "delims=" %%i in ('powershell -NoProfile -Command "$r=Invoke-RestMethod -Uri '%BASE_URL%/api/connections' -Headers @{Authorization='Bearer %TOKEN%'}; foreach($t in $r.data){ if($t.name -eq '%CONN_NAME%'){ $t.id } }"') do set "OLD_ID=%%i"
if not "%OLD_ID%"=="" (
    curl -s -X DELETE "%BASE_URL%/api/connections/%OLD_ID%" -H "%AUTH%" > nul
    echo [PASS] Removed old connection %CONN_NAME% ^(id=%OLD_ID%^)
    set /a PASS_COUNT+=1
)

for /f "delims=" %%i in ('powershell -NoProfile -Command "$b=@{name='%CONN_NAME%';dbType='%VERIFY_DB_TYPE%';host='%VERIFY_DB_HOST%';port=[int]%VERIFY_DB_PORT%;database='%VERIFY_DB_NAME%';username='%VERIFY_DB_USER%';password='%VERIFY_DB_PASS%'}|ConvertTo-Json; $r=Invoke-RestMethod -Uri '%BASE_URL%/api/connections' -Method Post -Headers @{Authorization='Bearer %TOKEN%'} -ContentType 'application/json' -Body $b; $r.data.id; $r.code"') do (
    if not defined CONN_ID ( set "CONN_ID=%%i" ) else ( set "CONN_CODE=%%i" )
)
if not "%CONN_ID%"=="" (
    echo [PASS] Created connection %CONN_NAME% id=%CONN_ID%
    set /a PASS_COUNT+=1
) else (
    echo [FAIL] Create connection failed: code=%CONN_CODE%
    set /a FAIL_COUNT+=1
)

rem ---------- 3. schema scan ----------
if not "%CONN_ID%"=="" (
    curl -s "%BASE_URL%/api/schema/%CONN_ID%/tables" -H "%AUTH%" | findstr /c:"users" > nul
    if %errorlevel%==0 ( echo [PASS] Tables include users & set /a PASS_COUNT+=1
    ) else ( echo [FAIL] Tables list abnormal & set /a FAIL_COUNT+=1 )

    curl -s "%BASE_URL%/api/schema/%CONN_ID%/tables/users" -H "%AUTH%" | findstr /c:"email" > nul
    if %errorlevel%==0 ( echo [PASS] users detail includes email & set /a PASS_COUNT+=1
    ) else ( echo [FAIL] Table detail abnormal & set /a FAIL_COUNT+=1 )
)

rem ---------- 4. generation (users 1000 + orders 2000, truncate first) ----------
if not "%CONN_ID%"=="" (
    for /f "delims=" %%i in ('powershell -NoProfile -Command "$b='{\"connectionId\":%CONN_ID%,\"truncateBefore\":true,\"batchSize\":1000,\"tables\":[{\"table\":\"users\",\"rowCount\":1000,\"columns\":{\"email\":{\"generator\":\"faker\",\"params\":{\"provider\":\"email\"}},\"name\":{\"generator\":\"faker\",\"params\":{\"provider\":\"name\"}}}},{\"table\":\"orders\",\"rowCount\":2000,\"columns\":{}}]}'; $r=Invoke-RestMethod -Uri '%BASE_URL%/api/generation/execute' -Method Post -Headers @{Authorization='Bearer %TOKEN%'} -ContentType 'application/json' -Body $b; $r.data.id; $r.code"') do (
        if not defined GEN_TASK ( set "GEN_TASK=%%i" ) else ( set "GEN_CODE=%%i" )
    )
    if not "%GEN_TASK%"=="" (
        echo [PASS] Generation task submitted taskId=%GEN_TASK%
        set /a PASS_COUNT+=1
        call :wait_task %GEN_TASK%
    ) else (
        echo [FAIL] Generation task submit failed: code=%GEN_CODE%
        set /a FAIL_COUNT+=1
    )
)

rem ---------- 5. masking (users.email/name with hmac) ----------
if not "%CONN_ID%"=="" (
    for /f "delims=" %%i in ('powershell -NoProfile -Command "$b='{\"connectionId\":%CONN_ID%,\"batchSize\":1000,\"tables\":[{\"table\":\"users\",\"columns\":{\"email\":{\"algorithm\":\"hmac\",\"params\":{},\"keyVersion\":1},\"name\":{\"algorithm\":\"hmac\",\"params\":{},\"keyVersion\":1}}}],\"verifyTables\":[{\"table\":\"users\",\"pkColumn\":\"id\",\"joinKeyColumn\":\"id\",\"whereClause\":\"\"}]}'; $r=Invoke-RestMethod -Uri '%BASE_URL%/api/masking/execute' -Method Post -Headers @{Authorization='Bearer %TOKEN%'} -ContentType 'application/json' -Body $b; $r.data.id; $r.code"') do (
        if not defined MASK_TASK ( set "MASK_TASK=%%i" ) else ( set "MASK_CODE=%%i" )
    )
    if not "%MASK_TASK%"=="" (
        echo [PASS] Masking task submitted taskId=%MASK_TASK%
        set /a PASS_COUNT+=1
        call :wait_task %MASK_TASK%
    ) else (
        echo [FAIL] Masking task submit failed: code=%MASK_CODE%
        set /a FAIL_COUNT+=1
    )
)

rem ---------- 6. JOIN consistency ----------
if not "%CONN_ID%"=="" (
    for /f "delims=" %%i in ('powershell -NoProfile -Command "$b='{\"connectionId\":%CONN_ID%,\"joinSql\":\"SELECT o.user_id FROM orders o WHERE o.user_id = ?\",\"targetTable\":\"users\",\"pkColumn\":\"id\",\"joinKeyColumn\":\"user_id\",\"whereClause\":\"\"}'; $r=Invoke-RestMethod -Uri '%BASE_URL%/api/masking/verify' -Method Post -Headers @{Authorization='Bearer %TOKEN%'} -ContentType 'application/json' -Body $b; $r.data.consistent"') do set "V_OK=%%i"
    if /i "%V_OK%"=="True" ( echo [PASS] JOIN consistency verified & set /a PASS_COUNT+=1
    ) else ( echo [FAIL] JOIN consistency failed & set /a FAIL_COUNT+=1 )
)

echo ==================================
echo Result: PASS=%PASS_COUNT% FAIL=%FAIL_COUNT%
if "%FAIL_COUNT%"=="0" (
    echo Deployment chain verified.
    endlocal & exit /b 0
)
endlocal & exit /b 1

rem ---- poll task until terminal state (max 240s) ----
:wait_task
set "TID=%~1"
for /l %%t in (1,1,120) do (
    timeout /t 2 /nobreak > nul
    for /f "delims=" %%s in ('powershell -NoProfile -Command "$r=Invoke-RestMethod -Uri '%BASE_URL%/api/tasks/%TID%' -Headers @{Authorization='Bearer %TOKEN%'}; $r.data.status"') do set "TSTATUS=%%s"
    if /i "!TSTATUS!"=="SUCCESS" ( echo [PASS] Task %TID% SUCCESS & set /a PASS_COUNT+=1 & exit /b 0 )
    if /i "!TSTATUS!"=="FAILED" ( echo [FAIL] Task %TID% FAILED & set /a FAIL_COUNT+=1 & exit /b 1 )
)
echo [FAIL] Task %TID% timeout
set /a FAIL_COUNT+=1
exit /b 1