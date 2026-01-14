/*
 * SPDX-License-Identifier: Apache-2.0 OR MPL-2.0
 * Copyright (c) 2021 Kaur Palang
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 * Modifications from commit d2fbac7328eda7b7a68348a4adcbb3a9961868b9 onward licensed under MPL-2.0
 */

package org.openintegrationengine.tlsmanager.server.backend;

public interface TrustStoreBackend {
    boolean persist(byte[] keystore);

    void init();

    byte[] load();

    char[] loadPassword();
}
