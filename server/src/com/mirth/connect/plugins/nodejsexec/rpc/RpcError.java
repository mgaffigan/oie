package com.mirth.connect.plugins.nodejsexec.rpc;

/**
 * JSON-RPC 2.0 error object.
 */
public record RpcError(
    int code,
    String message
) {
}
