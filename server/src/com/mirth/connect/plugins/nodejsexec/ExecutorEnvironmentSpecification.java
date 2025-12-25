package com.mirth.connect.plugins.nodejsexec;

import java.util.Map;

/**
 * Specification for a Node.js execution environment.
 * 
 * @param packages Map of npm package names to versions (e.g., "lodash" -> "4.17.21")
 */
public record ExecutorEnvironmentSpecification(Map<String, String> packages) {
    public ExecutorEnvironmentSpecification() {
        this(Map.of());
    }
}
