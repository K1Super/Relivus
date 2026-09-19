@echo off
rem ============================================================
rem Relivus meta DB restore script (Windows)
rem Usage: scripts\restore.bat backups\relivus_meta_xxx_TIMESTAMP.sql
rem Chooses the client by "mysql"/"pg" in the backup filename.
rem ============================================================
setlocal enabledelayedexpansion
cd /d "%~dp0.."

rem Allow overriding tool dirs, e.g. set "PG_BIN=D:\Deps\PostgreSQL\17\bin"
if "%PG_BIN%"=="" if exist "D:\Deps\PostgreSQL\17\bin" set "PG_BIN=D:\Deps\PostgreSQL\17\bin"
if "%MYSQL_BIN%"=="" if exist "D:\Deps\MySQL Server 8.0\bin" set "MYSQL_BIN=D:\Deps\MySQL Server 8.0\bin"

set "FILE=%~1"
if "%FILE%"=="" (
    echo [ERROR] Usage: scripts\restore.bat ^<backup-file^>
    echo Example: scripts\restore.bat backups\relivus_meta_mysql_20260919-00.sql
    exit /b 1
)
if not exist "%FILE%" (
    echo [ERROR] Backup file not found: %FILE%
    exit /b 1
)

if not exist .env (
    echo [ERROR] .env not found. Copy .env.example to .env first.
    exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%a in (".env") do (
    set "_k=%%a"
    set "_v=%%b"
    if not "!_k!"=="" if not "!_k:~0,1!"=="#" set "!_k!=!_v!"
)

if "!FILE!"=="!FILE:mysql=!" goto :pg

set "MYSQL_PWD=%RELIVUS_META_PASSWORD%"
set "MYSQLCLI=mysql"
if not "%MYSQL_BIN%"=="" set "MYSQLCLI=%MYSQL_BIN%\mysql"
echo [INFO] Restoring MySQL...
"%MYSQLCLI%" -h 127.0.0.1 -u "%RELIVUS_META_USER%" relivus_meta < "%FILE%"
if errorlevel 1 goto mysqlfail
goto :ok
:mysqlfail
echo [ERROR] mysql restore failed. Set MYSQL_BIN to the mysql.exe dir.
exit /b 1

:pg
set "PGHOST=127.0.0.1"
set "PGPASSWORD=%RELIVUS_META_PASSWORD%"
set "PSQLCLI=psql"
if not "%PG_BIN%"=="" set "PSQLCLI=%PG_BIN%\psql"
echo [INFO] Restoring PostgreSQL...
"%PSQLCLI%" -h %PGHOST% -U "%RELIVUS_META_USER%" -d relivus_meta -f "%FILE%"
if errorlevel 1 goto pgfail
goto :ok
:pgfail
echo [ERROR] psql restore failed. Set PG_BIN to the psql.exe dir.
exit /b 1

:ok
echo [OK] Restored: %FILE%
endlocal