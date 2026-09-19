@echo off
rem ============================================================
rem Relivus meta DB backup script (Windows)
rem Detects PostgreSQL / MySQL from RELIVUS_META_URL (.env) and
rem dumps the core business tables to backups\.
rem Usage: scripts\backup.bat
rem ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0.."

rem Allow overriding tool dirs, e.g. set "PG_BIN=D:\Deps\PostgreSQL\17\bin"
if "%PG_BIN%"=="" if exist "D:\Deps\PostgreSQL\17\bin" set "PG_BIN=D:\Deps\PostgreSQL\17\bin"
if "%MYSQL_BIN%"=="" if exist "D:\Deps\MySQL Server 8.0\bin" set "MYSQL_BIN=D:\Deps\MySQL Server 8.0\bin"

if not exist .env (
    echo [ERROR] .env not found. Copy .env.example to .env first.
    exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    set "_k=%%a"
    set "_v=%%b"
    if not "!_k!"=="" if not "!_k:~0,1!"=="#" set "!_k!=!_v!"
)

if "%RELIVUS_META_URL%"=="" ( echo [ERROR] RELIVUS_META_URL missing & exit /b 1 )
if "%RELIVUS_META_USER%"=="" ( echo [ERROR] RELIVUS_META_USER missing & exit /b 1 )
if "%RELIVUS_META_PASSWORD%"=="" ( echo [ERROR] RELIVUS_META_PASSWORD missing & exit /b 1 )

if not exist backups mkdir backups

for /f "delims=" %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmmss"') do set "TS=%%t"

set "META_TABLES=df_mask_mapping df_task df_task_log df_audit_log"

if not "%RELIVUS_META_URL:~0,15%"=="jdbc:postgresql" goto :mysql
set "PGHOST=127.0.0.1"
set "PGPASSWORD=%RELIVUS_META_PASSWORD%"
set "PGDUMP=pg_dump"
if not "%PG_BIN%"=="" set "PGDUMP=%PG_BIN%\pg_dump"
set "OUTFILE=backups\relivus_meta_pg_%TS%.sql"
echo [INFO] Backing up PostgreSQL...
"%PGDUMP%" -h %PGHOST% -U "%RELIVUS_META_USER%" -d relivus_meta -t df_mask_mapping -t df_task -t df_task_log -t df_audit_log > "%OUTFILE%"
if errorlevel 1 goto pgfail
echo [OK] Backup written: %OUTFILE%
exit /b 0
:pgfail
echo [ERROR] pg_dump failed. Set PG_BIN to the pg_dump.exe dir.
exit /b 1

:mysql
set "MYSQL_PWD=%RELIVUS_META_PASSWORD%"
set "MYSQLDUMP=mysqldump"
if not "%MYSQL_BIN%"=="" set "MYSQLDUMP=%MYSQL_BIN%\mysqldump"
set "OUTFILE=backups\relivus_meta_mysql_%TS%.sql"
echo [INFO] Backing up MySQL...
rem Remove quotes for the redirect to work
for %%i in ("%MYSQLDUMP%") do set "MYSQLDUMP_Q=%%~i"
"%MYSQLDUMP_Q%" -h 127.0.0.1 -u "%RELIVUS_META_USER%" relivus_meta %META_TABLES% > "%OUTFILE%"
if errorlevel 1 goto mysqldumpfail
echo [OK] Backup written: %OUTFILE%
endlocal
exit /b 0
:mysqldumpfail
echo [ERROR] mysqldump failed. Set MYSQL_BIN to the mysqldump.exe dir.
exit /b 1