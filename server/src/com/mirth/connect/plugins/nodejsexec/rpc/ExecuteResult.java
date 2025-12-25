package com.mirth.connect.plugins.nodejsexec.rpc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Result from the execute RPC method.
 */
public record ExecuteResult(
    JsonNode returnValue
) {
}
