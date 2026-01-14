/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.backend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SystemTrustStoreBackend implements TrustStoreBackend {

    @Override
    public boolean persist(byte[] keystore) {
        throw new UnsupportedOperationException("Persisting to system cacerts is not supported");
    }

    @Override
    public void init() {
        // Do nothing
    }

    @Override
    public byte[] load() {
        try {
            return Files.readAllBytes(resolveTrustStorePath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public char[] loadPassword() {
        var pwd = System.getProperty("javax.net.ssl.trustStorePassword", "changeit");
        return pwd.toCharArray();
    }

    private static Path resolveTrustStorePath() {
        // 1) If javax.net.ssl.trustStore is set, prefer it
        var prop = System.getProperty("javax.net.ssl.trustStore");
        if (prop != null && !"NONE".equalsIgnoreCase(prop)) {
            var p = Paths.get(prop);
            if (Files.exists(p)) return p;
        }

        // 2) Fallback to $JAVA_HOME/lib/security/jssecacerts or cacerts
        var secDir = Paths.get(System.getProperty("java.home"), "lib", "security");
        var jsse = secDir.resolve("jssecacerts");
        if (Files.exists(jsse)) return jsse;

        var cacerts = secDir.resolve("cacerts");
        if (Files.exists(cacerts)) return cacerts;

        throw new IllegalStateException("Could not locate system truststore (jssecacerts/cacerts).");
    }
}
