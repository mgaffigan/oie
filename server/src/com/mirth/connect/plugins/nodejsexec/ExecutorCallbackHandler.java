package com.mirth.connect.plugins.nodejsexec;

import com.fasterxml.jackson.databind.JsonNode;

public interface ExecutorCallbackHandler {
    /** 
     * Executes the given script with the provided arguments in JSON format.
     * 
     * @param script The script to execute.
     * @param argumentsJson The arguments in JSON format.
     * @return The result of the script execution as JSON.
     */
    JsonNode execute(String script, JsonNode argumentsJson);
}
