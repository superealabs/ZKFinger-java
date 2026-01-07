@echo off
REM Script de lancement pour Modern ZKFinger Demo

set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

set SRC_DIR=%SCRIPT_DIR%src
set BIN_DIR=%SCRIPT_DIR%bin
set LIB_DIR=%SCRIPT_DIR%lib
set MAIN_SRC=%SRC_DIR%\com\zkteco\biometric\ModernZKFingerDemo.java
set MAIN_CLASS=com.zkteco.biometric.ModernZKFingerDemo

REM Vérifications
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERREUR] Java n'est pas dans le PATH.
    pause
    exit /b 1
)

if not exist "%LIB_DIR%\ZKFingerReader.jar" (
    echo [ERREUR] ZKFingerReader.jar manquant.
    pause
    exit /b 1
)

if not exist "%BIN_DIR%" mkdir "%BIN_DIR%"

REM --- COMPILATION ---
set CLASSPATH_COMPILE="%LIB_DIR%\*"

REM Force la recompilation pour appliquer l'encodage
echo Compilation en cours (UTF-8)...

REM AJOUT DE -encoding UTF-8 ICI
javac -d "%BIN_DIR%" -cp %CLASSPATH_COMPILE% -encoding UTF-8 "%MAIN_SRC%"

if %ERRORLEVEL% NEQ 0 (
    echo [ECHEC] Erreur compilation.
    pause
    exit /b 1
)
echo Compilation reussie.

REM --- LANCEMENT ---
echo Lancement...
set CLASSPATH_RUN="%BIN_DIR%;%LIB_DIR%\*"
java -cp %CLASSPATH_RUN% %MAIN_CLASS%

pause