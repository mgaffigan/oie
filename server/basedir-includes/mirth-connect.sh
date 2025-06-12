#!/bin/bash

# Open Integration Engine Server Launcher Script

APP_ARGS=("$@")

# Set MIRTH_HOME to the script directory
MIRTH_HOME="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"
CLASSPATH="$MIRTH_HOME/mirth-server-launcher.jar"
VMOPTIONS=()

# Default JAVA_PATH based on env, JAVA_HOME, or "java"
if [[ -n "$JAVA_PATH" ]]; then
    JAVA_PATH="$JAVA_PATH"
elif [[ -n "$JAVA_HOME" && -x "$JAVA_HOME/bin/java" ]]; then
    JAVA_PATH="$JAVA_HOME/bin/java"
else
    # Default to "java" if no other options are available
    JAVA_PATH="java"
fi

# Set Java options
parse_vmoptions() {
    local file="$1"

    if [[ ! -f "$file" ]]; then
        echo "Error: VM options file not found: $file" >&2
        return 1
    fi

    # Read the file line by line
    while IFS= read -r line; do
        # Trim leading/trailing whitespace
        line=$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')

        # Skip empty lines and comments
        if [[ -z "$line" || "$line" =~ ^# ]]; then
            continue
        fi

        # Evaluate environment variables to be their actual values
        # Question: Is this a security risk?
        # Answer: No: vmoptions is trusted. We allow replacing JAVA_PATH and 
        #         CLASSPATH, so there is a by-design ability for someone with
        #         write privileges to the vmoptions to execute arbitrary code.
        line=$(eval echo "$line")
        
        # Check for -include-options directive
        if [[ "$line" =~ ^-include-options[[:space:]]+(.+) ]]; then
            local included_file="${BASH_REMATCH[1]}"

            # Resolve relative paths
            if [[ ! "$included_file" =~ ^/ ]]; then # Not an absolute path
                included_file="$(dirname "$file")/$included_file"
            fi

            # Recursively call parse_vmoptions for the included file
            parse_vmoptions "$included_file"
        elif [[ "$line" =~ ^-classpath[[:space:]]+(.+) ]]; then
            # Handle -classpath directive
            CLASSPATH="${BASH_REMATCH[1]}"
        elif [[ "$line" =~ ^-classpath/a[[:space:]]+(.+) ]]; then
            # Handle -classpath/a directive (append to existing classpath)
            CLASSPATH="${CLASSPATH}:${BASH_REMATCH[1]}"
        elif [[ "$line" =~ ^-classpath/p[[:space:]]+(.+) ]]; then
            # Handle -classpath/p directive (prepend to existing classpath)
            CLASSPATH="${BASH_REMATCH[1]}:${CLASSPATH}"
        elif [[ "$line" =~ ^-java-cmd[[:space:]]+(.+) ]]; then
            # Handle -java-cmd directive (set JAVA_PATH)
            JAVA_PATH="${BASH_REMATCH[1]}"
        else
            # Add the option to the accumulated string
            VMOPTIONS+=("$line")
        fi
    done < "$file"

    return 0
}

# Recursively parse the VM options file
parse_vmoptions "$MIRTH_HOME/oieserver.vmoptions"

JAVA_OPTS=("${VMOPTIONS[@]}" 
           "-cp" "$CLASSPATH" 
           "com.mirth.connect.server.launcher.MirthLauncher" 
           "${APP_ARGS[@]}")

# Launch Open Integration Engine (as this PID with exec)
echo "Starting Open Integration Engine..."
# This doesn't include quotes, which could be confusing. Not sure if there's a
# better way to do this.
echo "$JAVA_PATH ${JAVA_OPTS[*]}"
exec "$JAVA_PATH" "${JAVA_OPTS[@]}"
