@echo off
REM Burp SQLMap Extension Build Script for Windows

echo ========================================
echo Burp SQLMap Extension Builder
echo ========================================
echo.

REM Check if javac is available
where javac >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: javac not found. Please install JDK and add to PATH.
    pause
    exit /b 1
)

REM Check if jar command is available
where jar >nul 2>nul
if %ERRORLEVEL% NEQ 0 (
    echo Error: jar command not found. Please install JDK and add to PATH.
    pause
    exit /b 1
)

REM Find Burp Suite JAR
set BURP_JAR=

if exist "burpsuite_pro.jar" (
    set BURP_JAR=burpsuite_pro.jar
) else if exist "..\burpsuite_pro.jar" (
    set BURP_JAR=..\burpsuite_pro.jar
) else if exist "%USERPROFILE%\BurpSuitePro\burpsuite_pro.jar" (
    set BURP_JAR=%USERPROFILE%\BurpSuitePro\burpsuite_pro.jar
)

if "%BURP_JAR%"=="" (
    echo Warning: burpsuite_pro.jar not found automatically.
    echo Please ensure burpsuite_pro.jar is in the current directory.
    echo.
    set /p BURP_JAR="Enter path to burpsuite_pro.jar (or press Enter to use 'burpsuite_pro.jar'): "
    
    if "%BURP_JAR%"=="" (
        set BURP_JAR=burpsuite_pro.jar
    )
)

echo Using Burp JAR: %BURP_JAR%
echo.

REM Check if Burp JAR exists
if not exist "%BURP_JAR%" (
    echo Error: Burp Suite JAR not found at: %BURP_JAR%
    echo.
    echo To get the Burp API JAR:
    echo 1. Open Burp Suite Professional
    echo 2. Go to Extender ^> APIs
    echo 3. Click 'Save interface files'
    echo 4. Save to this directory as 'burpsuite_pro.jar'
    pause
    exit /b 1
)

REM Clean previous build
echo Cleaning previous build...
if exist burp rmdir /s /q burp
if exist sqlmap-extension.jar del sqlmap-extension.jar

REM Create package directory
mkdir burp 2>nul

REM Compile
echo Compiling SqlmapExtension.java...
javac -cp "%BURP_JAR%;." -d . SqlmapExtension.java

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Compilation failed!
    pause
    exit /b 1
)

REM Create JAR
echo Creating JAR file...
jar -cf sqlmap-extension.jar burp/*.class

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo JAR creation failed!
    pause
    exit /b 1
)

REM Clean up class files
echo Cleaning up...
rmdir /s /q burp

echo.
echo ========================================
echo Build successful!
echo ========================================
echo.
echo Output: sqlmap-extension.jar
echo.
echo To install in Burp Suite:
echo 1. Open Burp Suite Professional
echo 2. Go to Extender ^> Extensions
echo 3. Click 'Add'
echo 4. Select 'sqlmap-extension.jar'
echo 5. Click 'Next'
echo.
echo Extension will appear in the 'SQLMap' tab.
echo.
pause
