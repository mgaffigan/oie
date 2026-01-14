/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.backend;

import lombok.extern.slf4j.Slf4j;
import org.openintegrationengine.tlsmanager.shared.TLSPluginConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Slf4j
public class FileTrustStoreBackend implements TrustStoreBackend {

    private final Path keystorePath;
    private final char[] storepass;

    public FileTrustStoreBackend(String keystorePath) {
        this(keystorePath, System.getenv(TLSPluginConstants.ENV_PERSISTENCE_FS_TRUSTSTOREPASS));
    }

    public FileTrustStoreBackend(String keystorePath, String storePass) {
        this.keystorePath = Paths.get(keystorePath);

        if (storePass == null) {
            throw new IllegalStateException("TrustStore password not set");
        }

        this.storepass = storePass.toCharArray();
    }

    @Override
    public boolean persist(byte[] keystore) {
        final var openOptions = new OpenOption[]{
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.CREATE
        };

        try {
            Files.write(keystorePath, keystore, openOptions);
            return true;
        } catch (IOException e) {
            log.error("Error persisting keystore to file", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init() {
        if (Files.exists(keystorePath)) {
            log.debug("Using existing keystore at {}", keystorePath);
            return;
        }

        try {
            var keystore = KeyStore.getInstance(TLSPluginConstants.PKCS12);
            keystore.load(null, storepass);

            try (var baos = new ByteArrayOutputStream()) {
                keystore.store(baos, storepass);
                persist(baos.toByteArray());
            }

        } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            log.error("Error initializing keystore", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] load() {
        try {
            return Files.readAllBytes(keystorePath);
        } catch (IOException e) {
            log.error("Error reading keystore at {}", keystorePath, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public char[] loadPassword() {
        if (storepass == null) {
            throw new IllegalStateException("Store password not set");
        }
        return storepass;
    }
}
