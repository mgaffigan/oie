package com.mirth.connect.plugins.nodejsexec.rpc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * JSON-RPC 2.0 request message.
 */
public record RpcRequest(
    String jsonrpc,
    String method,
    JsonNode params,
    int id
) {
    public RpcRequest(String method, JsonNode params, int id) {
        this("2.0", method, params, id);
    }
}
