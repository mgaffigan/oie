package com.mirth.connect.plugins.nodejsexec.rpc;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.mirth.connect.plugins.nodejsexec.ExecutorException;

/**
 * Handler for incoming JSON-RPC requests from the remote side.
 */
public interface RpcRequestHandler {
    /**
     * Handles an incoming request.
     * 
     * @param method The RPC method name
     * @param params The parameters as a JsonNode
     * @param responseSender Sender to send exactly one response back
     */
    void handleRequest(String method, JsonNode params, RpcResponseSender responseSender)
            throws ExecutorException, IOException;
}
