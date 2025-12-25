package com.mirth.connect.plugins.nodejsexec.rpc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 response message.
 */
public record RpcResponse(
    String jsonrpc,
    JsonNode result,
    RpcError error,
    int id
) {
    public RpcResponse(JsonNode result, int id) {
        this("2.0", result, null, id);
    }
    
    public RpcResponse(RpcError error, int id) {
        this("2.0", null, error, id);
    }
}
