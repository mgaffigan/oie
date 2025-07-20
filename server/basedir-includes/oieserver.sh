#!/bin/sh

java @appdata/launch.args com.mirth.connect.server.launcher.VmOptionsLauncher "$@"
# if exit code == 75, then the arguments changed and we need to restart
if [ $? -eq 75 ]; then
    exec "$0" "$@"
fi
