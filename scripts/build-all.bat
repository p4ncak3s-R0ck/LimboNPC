@echo off
setlocal
cd /d "%~dp0\.."

if exist build\artifacts rmdir /s /q build\artifacts
mkdir build\artifacts
if exist limbo\build\libs\LimboNPC-Limbo-*.jar del /q limbo\build\libs\LimboNPC-Limbo-*.jar
if exist velocity\build\libs\LimboNPC-Velocity-*.jar del /q velocity\build\libs\LimboNPC-Velocity-*.jar

for %%V in (26 1.21 1.20) do (
  echo Building compatibility range %%V.x
  call gradlew.bat --no-daemon :common:test :limbo:jar :velocity:jar -PminecraftVersion=%%V
  if errorlevel 1 exit /b 1
  copy /y "limbo\build\libs\LimboNPC-Limbo-%%V.jar" build\artifacts\ >nul
  copy /y "velocity\build\libs\LimboNPC-Velocity-%%V.jar" build\artifacts\ >nul
)

echo.
echo Artifacts:
dir /b build\artifacts\*.jar
