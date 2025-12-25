package com.mirth.connect.plugins.nodejsexec.rpc;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Allows sending exactly one response to an RPC request.
 * The request ID is managed internally.
 */
public interface RpcResponseSender {
    /**
     * Sends a successful response with the given result.
     * Can only be called once.
     * 
     * @param result The result to send
     * @throws IOException if sending fails
     * @throws IllegalStateException if already sent
     */
    void sendResponse(JsonNode result) throws IOException;
    
    /**
     * Sends an error response.
     * Can only be called once.
     * 
     * @param code Error code
     * @param message Error message
     * @throws IOException if sending fails
     * @throws IllegalStateException if already sent
     */
    void sendError(int code, String message) throws IOException;
}
