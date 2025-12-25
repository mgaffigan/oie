package com.mirth.connect.plugins.nodejsexec;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.HashMap;

/**
 * Cache for Node.js execution environments based on script annotations.
 */
public class NodeJsEnvironmentCache {
    private final ExecutorRpcConnection rpcConnection;
    private ConcurrentMap<String, ExecutorEnvironmentConnection> environmentConnections = new ConcurrentHashMap<>();

    public NodeJsEnvironmentCache(ExecutorRpcConnection rpcConnection) {
        this.rpcConnection = rpcConnection;
    }

    public synchronized ExecutorEnvironmentConnection getForScript(String script)
            throws ExecutorException {
        var environmentConfig = getEnvironmentConfig(script);
        var env = environmentConnections.get(environmentConfig);
        if (env != null)
            return env;

        env = rpcConnection.createEnvironment(parseEnvironmentSpecification(environmentConfig));
        environmentConnections.put(environmentConfig, env);
        return env;
    }

    private String getEnvironmentConfig(String script) {
        // Find all lines that start with "// @env " and return the rest of the
        // line as the environment config

        // Short circuit if there are no environment annotations
        final String ENV_PREFIX = "// @env ";
        if (!script.contains(ENV_PREFIX)) {
            return "";
        }

        var sb = new StringBuilder();
        var lines = script.split("\n");
        for (var line : lines) {
            line = line.trim();
            if (line.startsWith(ENV_PREFIX)) {
                sb.append(line.substring(ENV_PREFIX.length()).trim());
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private ExecutorEnvironmentSpecification parseEnvironmentSpecification(String environmentConfig)
            throws ExecutorException {
        var packages = new HashMap<String, String>();
        var lines = environmentConfig.split("\n");
        for (var line : lines) {
            if (line.isBlank()) continue;

            // Lines that start with "install " indicate packages to install
            final String INSTALL_PREFIX = "install ";
            if (line.startsWith(INSTALL_PREFIX)) {
                String packageNameAndVersion = line.substring(INSTALL_PREFIX.length()).trim();
                // Split into name and version
                var parts = packageNameAndVersion.split("@", 2);
                packages.put(parts[0], parts.length > 1 ? parts[1] : "latest");
            } else {
                throw new ExecutorException("Invalid environment specification line: " + line);
            }
        }
        return new ExecutorEnvironmentSpecification(packages);
    }
}
