package com.mirth.connect.plugins.nodejsexec.rpc;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Parameters for the execute RPC method.
 */
public record ExecuteParams(
    String envId,
    String callbackEnvId,
    String script,
    JsonNode args
) {
}
