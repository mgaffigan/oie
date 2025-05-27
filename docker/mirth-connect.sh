#!/bin/bash

# Mirth Connect Server Launcher Script

# Get the directory where this script is located
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )"

# Set MIRTH_HOME to the script directory
export MIRTH_HOME="$SCRIPT_DIR"

# Set Java options
parse_vmoptions() {
    local file="$1"
    local current_options="${2:-}" # Initialize with existing options, or empty

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

        # Check for -include-options directive
        if [[ "$line" =~ ^-include-options[[:space:]]+(.+) ]]; then
            local included_file="${BASH_REMATCH[1]}"

            # Resolve relative paths
            if [[ ! "$included_file" =~ ^/ ]]; then # Not an absolute path
                included_file="$(dirname "$file")/$included_file"
            fi

            # Recursively call parse_vmoptions for the included file
            local included_opts
            if ! included_opts=$(parse_vmoptions "$included_file" "$current_options"); then
                echo "Error processing included options from $included_file" >&2
                return 1
            fi
            current_options="$included_opts"
        else
            # Add the option to the accumulated string
            current_options+="${current_options:+" "}$line"
        fi
    done < "$file"

    echo "$current_options"
    return 0
}
JAVA_OPTS=$(parse_vmoptions "oieserver.vmoptions")
JAVA_OPTS="$JAVA_OPTS -Dmirth.home=$MIRTH_HOME"

# Launch Mirth Connect
echo "Starting Mirth Connect..."
echo "MIRTH_HOME: $MIRTH_HOME"
echo "JAVA_OPTS: $JAVA_OPTS"

java $JAVA_OPTS -jar "$MIRTH_HOME/mirth-server-launcher.jar"
