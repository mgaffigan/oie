@echo off

REM Set the script directory as the working directory
cd %~dp0

REM Use the local jre, JAVA_HOME environment variable, or default java command.
SET "JAVA_CMD=java"
IF DEFINED JAVA_HOME SET "JAVA_CMD=%JAVA_HOME%\bin\java"
IF EXIST "jre\bin\java.exe" SET "JAVA_CMD=jre\bin\java"

REM Run the Java command with the specified arguments
"%JAVA_CMD%" @appdata/launch.args com.mirth.connect.server.launcher.VmOptionsLauncher %*

REM if exit code == 75, then the arguments changed and we need to restart
IF %ERRORLEVEL% EQU 75 (
    "%JAVA_CMD%" @appdata/launch.args com.mirth.connect.server.launcher.VmOptionsLauncher %*
)

REM if exit code != 0, maybe wrong version of Java?
IF NOT %ERRORLEVEL% == 0 (
    ECHO ERROR %ERRORLEVEL%: Failed to start Mirth Connect using Java '%JAVA_CMD%'.>&2
    ECHO Please ensure %JAVA_HOME% refers to Java 17 or later.>&2
)

EXIT /B %ERRORLEVEL%
