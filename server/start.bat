@echo off
REM PvP Practice server launcher for Windows.
REM   - downloads the latest Paper build for MC_VERSION (PaperMC Fill v3 API, v2 fallback)
REM   - accepts the Minecraft EULA (https://aka.ms/MinecraftEULA) on your behalf: running this script means you agree
REM   - starts Paper with Aikar's G1 flags
REM Usage: start.bat [memory]   e.g. start.bat 8G     (set UPDATE_PAPER=1 to re-download Paper)
setlocal EnableDelayedExpansion
cd /d "%~dp0"

if "%MC_VERSION%"=="" set MC_VERSION=1.21.11
set MEMORY=%1
if "%MEMORY%"=="" set MEMORY=6G

where java >nul 2>nul || (echo [start] Java 21+ is required. & pause & exit /b 1)

if not exist paper.jar set NEED_PAPER=1
if "%UPDATE_PAPER%"=="1" set NEED_PAPER=1
if "%NEED_PAPER%"=="1" (
  echo [start] Downloading Paper %MC_VERSION%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$ErrorActionPreference='Stop'; $ua='pvp-practice-start-script/1.0';" ^
    "try { $b = Invoke-RestMethod -UserAgent $ua -Uri 'https://fill.papermc.io/v3/projects/paper/versions/%MC_VERSION%/builds/latest'; $d = $b.downloads.'server:default'; $url = $d.url; $sha = $d.checksums.sha256 }" ^
    "catch { $v2 = Invoke-RestMethod -UserAgent $ua -Uri 'https://api.papermc.io/v2/projects/paper/versions/%MC_VERSION%/builds'; $last = $v2.builds[-1]; $url = 'https://api.papermc.io/v2/projects/paper/versions/%MC_VERSION%/builds/' + $last.build + '/downloads/' + $last.downloads.application.name; $sha = $last.downloads.application.sha256 }" ^
    "Invoke-WebRequest -UserAgent $ua -Uri $url -OutFile 'paper.jar.tmp';" ^
    "if ($sha -and ((Get-FileHash 'paper.jar.tmp' -Algorithm SHA256).Hash.ToLower() -ne $sha.ToLower())) { Remove-Item 'paper.jar.tmp'; throw 'Checksum mismatch' }" ^
    "Move-Item -Force 'paper.jar.tmp' 'paper.jar'"
  if errorlevel 1 (echo [start] Paper download failed. & pause & exit /b 1)
)

if not exist plugins\PvPCore.jar (
  echo [start] plugins\PvPCore.jar missing. Run "mvn clean package" in the project root first.
  pause
  exit /b 1
)

findstr /b "eula=true" eula.txt >nul 2>nul || (
  echo [start] Accepting the Minecraft EULA ^(https://aka.ms/MinecraftEULA^).
  > eula.txt echo eula=true
)

set G1=-XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=40 -XX:G1HeapRegionSize=8M -XX:G1ReservePercent=20 -XX:InitiatingHeapOccupancyPercent=15
set MEMNUM=%MEMORY:G=%
if %MEMNUM% GEQ 12 set G1=-XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=15 -XX:InitiatingHeapOccupancyPercent=20

echo [start] Starting Paper %MC_VERSION% with %MEMORY% heap
java -Xms%MEMORY% -Xmx%MEMORY% -XX:+AlwaysPreTouch -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:+PerfDisableSharedMem -XX:+UnlockExperimentalVMOptions -XX:+UseG1GC %G1% -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:MaxGCPauseMillis=200 -XX:MaxTenuringThreshold=1 -XX:SurvivorRatio=32 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -jar paper.jar --nogui
pause
