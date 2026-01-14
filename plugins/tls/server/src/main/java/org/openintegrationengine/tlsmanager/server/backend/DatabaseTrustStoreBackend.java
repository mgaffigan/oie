/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.backend;

import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import lombok.extern.slf4j.Slf4j;
import org.openintegrationengine.tlsmanager.shared.TLSPluginConstants;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Base64;

@Slf4j
public class DatabaseTrustStoreBackend implements TrustStoreBackend {

    private final ConfigurationController configurationController;

    private final String databaseColumn;

    public DatabaseTrustStoreBackend(String databaseColumn) {
        this.configurationController = ControllerFactory.getFactory().createConfigurationController();
        this.databaseColumn = databaseColumn;
    }

    @Override
    public boolean persist(byte[] keystore) {
        var encoder = Base64.getEncoder();
        var b64Keystore = encoder.encodeToString(keystore);
        configurationController.saveProperty(TLSPluginConstants.PLUGIN_POINTNAME, databaseColumn, b64Keystore);
        return false;
    }

    @Override
    public void init() {
        var keystoreBytes = configurationController.getProperty(TLSPluginConstants.PLUGIN_POINTNAME, databaseColumn);
        if (keystoreBytes != null) {
            log.debug("Using existing keystore from config column {}", databaseColumn);
            return;
        }

        try {
            var keystore = KeyStore.getInstance(TLSPluginConstants.PKCS12);
            keystore.load(null, new char[0]);

            try (var baos = new ByteArrayOutputStream()) {
                keystore.store(baos, new char[0]);
                persist(baos.toByteArray());
            }

        } catch (IOException | KeyStoreException | CertificateException | NoSuchAlgorithmException e) {
            log.error("Error initializing keystore", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] load() {
        var decoder = Base64.getDecoder();
        var keystoreBytes = configurationController.getProperty(TLSPluginConstants.PLUGIN_POINTNAME, databaseColumn);
        return decoder.decode(keystoreBytes);
    }

    @Override
    public char[] loadPassword() {
        return new char[0];
    }
}
