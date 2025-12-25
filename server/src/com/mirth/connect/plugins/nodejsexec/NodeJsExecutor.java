package com.mirth.connect.plugins.nodejsexec;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * API used from Rhino to execute Node.js code.
 */
public class NodeJsExecutor {
    private static NodeJsEnvironmentCache environmentCache;
    private static ObjectMapper objectMapper = new ObjectMapper();

    private NodeJsExecutor() {
        // Prevent instantiation
    }

    private synchronized static NodeJsEnvironmentCache getCache() throws IOException {
        if (environmentCache == null) {
            environmentCache = new NodeJsEnvironmentCache(new ExecutorRpcConnection("/tmp/node-rpc.sock"));
        }
        return environmentCache;
    }

    public static String run(String script, String argsJson) throws Exception {
        var env = getCache().getForScript(script);
        var argsNode = objectMapper.readTree(argsJson);
        var resp = env.execute(script, argsNode, new ExecutorCallbackHandler() {
            @Override
            public JsonNode execute(String script, JsonNode argumentsJson) {
                throw new RuntimeException("Callbacks are not supported in this context");
            }
        });
        return objectMapper.writeValueAsString(resp);
    }
}
