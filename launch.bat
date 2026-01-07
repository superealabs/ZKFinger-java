@echo off
REM Script de lancement pour ZKFinger Demo
REM Ce script compile et lance l'application Java

REM Obtenir le répertoire du script
set SCRIPT_DIR=%~dp0
cd /d "%SCRIPT_DIR%"

REM Définir les chemins
set SRC_DIR=%SCRIPT_DIR%src
set BIN_DIR=%SCRIPT_DIR%bin
set LIB_DIR=%SCRIPT_DIR%lib
set JAR_FILE=%LIB_DIR%\ZKFingerReader.jar

REM Vérifier que Java est installé
where java >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Erreur: Java n'est pas trouve dans le PATH.
    echo Veuillez installer Java ou ajouter Java au PATH.
    pause
    exit /b 1
)

REM Vérifier que le JAR existe
if not exist "%JAR_FILE%" (
    echo Erreur: Le fichier %JAR_FILE% est introuvable.
    pause
    exit /b 1
)

REM Créer le répertoire bin s'il n'existe pas
if not exist "%BIN_DIR%" (
    mkdir "%BIN_DIR%"
)

REM Compiler le code source si nécessaire
REM Vérifier si les sources sont plus récentes que les classes compilées
set NEED_COMPILE=0
if not exist "%BIN_DIR%\com\zkteco\biometric\ZKFPDemo.class" (
    set NEED_COMPILE=1
) else (
    REM Comparer les dates de modification
    for %%A in ("%SRC_DIR%\com\zkteco\biometric\ZKFPDemo.java") do set SRC_TIME=%%~tA
    for %%A in ("%BIN_DIR%\com\zkteco\biometric\ZKFPDemo.class") do set BIN_TIME=%%~tA
    if "%SRC_TIME%" GTR "%BIN_TIME%" (
        set NEED_COMPILE=1
    )
)

if %NEED_COMPILE%==1 (
    echo Compilation en cours...
    javac -d "%BIN_DIR%" -cp "%JAR_FILE%" "%SRC_DIR%\com\zkteco\biometric\ZKFPDemo.java"
    if %ERRORLEVEL% NEQ 0 (
        echo Erreur lors de la compilation.
        pause
        exit /b 1
    )
    echo Compilation reussie.
) else (
    echo Utilisation des classes deja compilees.
)

REM Lancer l'application
echo Lancement de l'application...
java -cp "%BIN_DIR%;%JAR_FILE%" com.zkteco.biometric.ZKFPDemo

REM Garder le terminal ouvert
pause

