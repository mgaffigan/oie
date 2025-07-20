@echo off

java @appdata/launch.args com.mirth.connect.server.launcher.VmOptionsLauncher %*
REM if exit code == 75, then the arguments changed and we need to restart
IF %ERRORLEVEL% EQU 75 (
    CALL "%~f0" %*
)
