package com.mirth.connect.plugins.nodejsexec.rpc;

import java.util.Map;

/**
 * Parameters for the initialize RPC method.
 */
public record InitializeParams(
    EnvOptions envOptions
) {
    public record EnvOptions(
        Map<String, String> packages
    ) {}
}
