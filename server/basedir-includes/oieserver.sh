#!/bin/sh

# Set the script directory as the working directory
cd "$(dirname -- "$0")" || exit 1

# Use the local jre, JAVA_HOME environment variable, or default java command.
JAVA_CMD="java"
[ -n "${JAVA_HOME}" ] && JAVA_CMD="${JAVA_HOME}/bin/java"
[ -f "jre/bin/java" ] && JAVA_CMD="jre/bin/java"

# Run the Java command with the specified arguments
"$JAVA_CMD" @appdata/launch.args com.mirth.connect.server.launcher.VmOptionsLauncher "$@"

# if exit code == 75, then the arguments changed and we need to restart
if [ $? -eq 75 ]; then
    "$JAVA_CMD" @appdata/launch.args com.mirth.connect.server.launcher.VmOptionsLauncher "$@"
fi

# if exit code == 1, maybe wrong version of Java?
if [ $? -ne 0 ]; then
    echo "ERROR $?: Failed to start Mirth Connect using Java '$JAVA_CMD'." >&2
    echo "Please ensure JAVA_HOME refers to Java 17 or later." >&2
fi

exit $?
