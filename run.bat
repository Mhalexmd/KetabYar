@echo off
setlocal
cd /d "%~dp0"
if not exist out mkdir out
where javac >nul 2>nul
if errorlevel 1 (
  echo Java JDK was not found. Install JDK 17 or newer and add it to PATH.
  pause
  exit /b 1
)
if not exist "lib\sqlite-jdbc-*.jar" (
  echo SQLite JDBC driver not found in the lib folder. See README.md for download instructions.
  pause
  exit /b 1
)
echo Compiling project...
dir /s /b src\main\java\*.java > out\sources.txt
javac -encoding UTF-8 -cp "lib\*" -d out @out\sources.txt
del out\sources.txt >nul 2>nul
if errorlevel 1 (
  echo.
  echo Compilation failed. Check the error messages above.
  pause
  exit /b 1
)
echo Starting Library Management System...
java -cp "out;lib\*" com.example.library.Main %*
endlocal
