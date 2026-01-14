/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openintegrationengine.tlsmanager.shared.PersistenceMode;
import org.openintegrationengine.tlsmanager.shared.models.TLSPluginConfiguration;

@ExtendWith(MockitoExtension.class)
public class CertificateServiceTest {

    private CertificateService certificateService;

    //@BeforeEach
    public void setUp() {
        certificateService = new CertificateService(null);
    }

    //@Test
    public void testSetTrustStore() {
        var pluginConfiguration = new TLSPluginConfiguration(
            PersistenceMode.FILESYSTEM,
            "/path/to",
            "truststorePass",
            "/path/to",
            "keystorePass",
            false
        );

        certificateService.init(pluginConfiguration);
    }
}
