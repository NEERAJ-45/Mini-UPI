@echo off
echo ==================================================
echo Building project and installing dependencies...
echo ==================================================
call .\mvnw.cmd clean install -DskipTests

echo.
echo ==================================================
echo Running all tests across all microservices...
echo ==================================================
call .\mvnw.cmd test

echo.
echo ==================================================
echo Done!
echo ==================================================
pause
