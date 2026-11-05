@echo off
setlocal
pushd "%~dp0" || exit /b 1

rem JDK 25 en Windows necesita una ruta temporal valida para sockets AF_UNIX.
if not exist "build\tmp" mkdir "build\tmp"
if errorlevel 1 goto :failed
set JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Djdk.net.unixdomain.tmpdir="%CD%\build\tmp"

call "%~dp0gradlew.bat" --no-daemon build %*
if errorlevel 1 goto :failed

rem Lee el nombre y la version para copiar solo el JAR instalable.
set "archives_base_name="
set "mod_version="
for /f "usebackq tokens=1,2 delims==" %%A in ("gradle.properties") do (
    if "%%A"=="archives_base_name" set "archives_base_name=%%B"
    if "%%A"=="mod_version" set "mod_version=%%B"
)
set "artifact=build\libs\%archives_base_name%-%mod_version%.jar"
set "destination=%~dp0..\..\jar\LiveCrops"
if not exist "%artifact%" (
    echo ERROR: No se ha generado "%artifact%".
    popd
    exit /b 1
)
if not exist "%destination%" mkdir "%destination%"
if errorlevel 1 goto :failed
copy /y "%artifact%" "%destination%\" >nul
if errorlevel 1 goto :failed

echo JAR listo: "%destination%\%archives_base_name%-%mod_version%.jar"
popd
exit /b 0

:failed
set "build_exit_code=%errorlevel%"
echo ERROR: No se ha podido compilar o copiar el JAR.
popd
exit /b %build_exit_code%
