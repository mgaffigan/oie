package com.mirth.connect.plugins.nodejsexec;

import com.mirth.connect.plugins.nodejsexec.rpc.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Represents a connection to a specific Node.js execution environment.
 * This class is AutoCloseable and will dispose the environment when closed.
 */
public class ExecutorEnvironmentConnection implements AutoCloseable {
    private final RpcConnection rpcConnection;
    private final ObjectMapper objectMapper;
    private final ExecutorRpcConnection.CallbackRegistry callbackRegistry;
    private final String envId;
    private volatile boolean disposed = false;

    /**
     * Creates a new environment connection.
     * 
     * @param rpcConnection    The RPC connection to use
     * @param objectMapper     The JSON object mapper
     * @param callbackRegistry The callback registry for registering handlers
     * @param envId            The environment ID
     */
    ExecutorEnvironmentConnection(RpcConnection rpcConnection, ObjectMapper objectMapper,
            ExecutorRpcConnection.CallbackRegistry callbackRegistry, String envId) {
        this.rpcConnection = rpcConnection;
        this.objectMapper = objectMapper;
        this.callbackRegistry = callbackRegistry;
        this.envId = envId;
    }

    /**
     * Executes the given script with the provided arguments in JSON format.
     * 
     * @param script          The Node.js script to execute
     * @param argumentsJson   The arguments in JSON format (as a JSON object string)
     * @param callbackHandler The callback handler for executing nested scripts (can
     *                        be null)
     * @return The result of the script execution as JSON
     * @throws ExecutorException if execution fails
     */
    public JsonNode execute(String script, JsonNode argumentsJson, ExecutorCallbackHandler callbackHandler)
            throws ExecutorException {
        if (disposed) {
            throw new ExecutorException("Environment has been disposed");
        }

        try (var cb = callbackRegistry.registerCallback(callbackHandler)) {
            JsonNode params = objectMapper.valueToTree(
                    new ExecuteParams(envId, cb.getKey(), script, argumentsJson));
            JsonNode result = rpcConnection.sendRequest("execute", params);
            JsonNode returnValue = result.get("returnValue");
            return returnValue;
        }
    }

    /**
     * Gets the environment ID.
     * 
     * @return The environment ID
     */
    public String getEnvId() {
        return envId;
    }

    /**
     * Disposes of this environment, cleaning up resources on the Node.js side.
     * After calling this method, the environment can no longer be used.
     * 
     * @throws ExecutorException if disposal fails
     */
    @Override
    public void close() throws ExecutorException {
        if (disposed) {
            return;
        }
        disposed = true;

        JsonNode params = objectMapper.valueToTree(new DisposeParams(envId));
        rpcConnection.sendRequest("dispose", params);
    }
}
