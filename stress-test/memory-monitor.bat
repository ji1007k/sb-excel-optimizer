@echo off
echo =========================================
echo Excel Download Performance Monitor
echo =========================================
echo.
echo 메모리 사용량을 모니터링합니다...
echo Ctrl+C로 중단할 수 있습니다.
echo.

:loop
for /f "tokens=2 delims==" %%i in ('wmic OS get TotalVisibleMemorySize /format:value') do set totalmem=%%i
for /f "tokens=2 delims==" %%i in ('wmic OS get FreePhysicalMemory /format:value') do set freemem=%%i

set /a usedmem=%totalmem%-%freemem%
set /a usedpercent=%usedmem%*100/%totalmem%

echo [%time%] 메모리 사용률: %usedpercent%%% (사용: %usedmem%KB / 전체: %totalmem%KB)

timeout /t 2 /nobreak >nul
goto loop
