@echo off
setlocal
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java 21 is required. Install JDK 21 and reopen this window.
  pause
  exit /b 1
)
set "LSRS_JAR=dist\lsrs-sprint1.jar"
if not exist "%LSRS_JAR%" set "LSRS_JAR=build\libs\lsrs-sprint1.jar"
if not exist "%LSRS_JAR%" (
  echo JAR missing. Run gradlew.bat clean test bootJar first.
  pause
  exit /b 1
)
echo LSRS local: http://localhost:8080
echo Uses real time and persistent local data. Sign up first.
echo Development verification mailbox: http://localhost:8080/dev/mailbox
java -jar "%LSRS_JAR%" --spring.profiles.active=local
pause
