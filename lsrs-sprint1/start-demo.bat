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
echo LSRS demo: http://localhost:8080
echo Fixed demo clock: 2026-11-14 10:00 Asia/Seoul
echo Student: student@example.test / Demo1234!
echo Admin: admin@example.test / Demo1234!
echo Keep this window open. Press Ctrl+C to stop.
java -jar "%LSRS_JAR%" --spring.profiles.active=demo
pause
