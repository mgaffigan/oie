package com.mirth.connect.plugins.nodejsexec;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.plugins.nodejsexec.rpc.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * High-level interface to the Node.js executor service.
 * Handles environment initialization, script execution, and callbacks.
 */
public class ExecutorRpcConnection implements AutoCloseable {

    /**
     * Interface for registering callback handlers during execution.
     */
    interface CallbackRegistry {

        /**
         * Registers a callback handler for the duration of an execution.
         * Returns an AutoCloseable registration that will unregister when closed.
         */
        CallbackRegistration registerCallback(ExecutorCallbackHandler handler);
    }

    /**
     * Represents a registered callback that can be closed to unregister.
     */
    interface CallbackRegistration extends AutoCloseable {
        /**
         * Gets the registration key.
         */
        String getKey();

        @Override
        void close();
    }

    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Logger logger = LogManager.getLogger(ExecutorRpcConnection.class);

    private final RpcConnection rpcConnection;
    private final ConcurrentHashMap<String, ExecutorCallbackHandler> callbackHandlers = new ConcurrentHashMap<>();
    private final AtomicInteger nextCallbackId = new AtomicInteger(1);
    private final CallbackRegistry registry;

    /**
     * Creates a new connection to the Node.js RPC server at the specified path.
     * 
     * @param socketPath Path to the Unix domain socket
     * @throws IOException if connection fails
     */
    public ExecutorRpcConnection(String socketPath) throws IOException {
        // Create RPC connection with handler for incoming callback requests
        this.rpcConnection = new RpcConnection(socketPath, this::handleCallbackRequest);
        this.registry = new CallbackRegistry() {
            @Override
            public CallbackRegistration registerCallback(ExecutorCallbackHandler handler) {
                String key = "cb-" + nextCallbackId.getAndIncrement();
                callbackHandlers.put(key, handler);
                return new CallbackRegistration() {
                    @Override
                    public String getKey() {
                        return key;
                    }

                    @Override
                    public void close() {
                        callbackHandlers.remove(key);
                    }
                };
            }
        };
    }

    /**
     * Initializes a new execution environment.
     * 
     * @param spec Environment specification with packages
     * @return A connection to the initialized environment
     * @throws ExecutorException if initialization fails
     */
    public ExecutorEnvironmentConnection createEnvironment(ExecutorEnvironmentSpecification spec)
            throws ExecutorException {
        JsonNode params = objectMapper.valueToTree(new InitializeParams(
                new InitializeParams.EnvOptions(spec.packages())));
        JsonNode result = rpcConnection.sendRequest("initialize", params);
        String envId = result.get("envId").asText();

        return new ExecutorEnvironmentConnection(rpcConnection, objectMapper, registry, envId);
    }

    /**
     * Handles incoming callback requests from Node.js.
     * This is called when Node.js script calls the callback function.
     */
    private void handleCallbackRequest(String method, JsonNode params, RpcResponseSender responseSender)
            throws ExecutorException, IOException{
        // Only handle "execute" callback requests
        if (!"execute".equals(method)) {
            throw new ExecutorException(-32601, "Method not found: " + method);
        }

        if (params == null || params.isNull()) {
            throw new ExecutorException(-32602, "Invalid params: params object is required");
        }

        JsonNode envIdNode = params.get("envId");
        if (envIdNode == null || envIdNode.isNull()) {
            throw new ExecutorException(-32602, "Invalid params: envId is required");
        }
        String envId = envIdNode.asText();
        ExecutorCallbackHandler handler = callbackHandlers.get(envId);
        if (handler == null) {
            throw new ExecutorException(-32603, "Callback handler not found for envId: " + envId);
        }

        JsonNode scriptNode = params.get("script");
        if (scriptNode == null || scriptNode.isNull()) {
            throw new ExecutorException(-32602, "Invalid params: script is required");
        }
        String script = scriptNode.asText();

        // Execute the callback in the current Rhino context
        JsonNode args = params.get("args");
        JsonNode resultJson = handler.execute(script, args);

        // Send response back to Node.js
        responseSender.sendResponse(objectMapper.valueToTree(new ExecuteResult(resultJson)));
    }

    @Override
    public void close() throws ExecutorException {
        rpcConnection.close();
    }
}
