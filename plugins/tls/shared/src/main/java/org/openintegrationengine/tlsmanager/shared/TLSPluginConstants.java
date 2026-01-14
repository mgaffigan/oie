/*
 * SPDX-License-Identifier: Apache-2.0 OR MPL-2.0
 * Copyright (c) 2021 Kaur Palang
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 * Modifications from commit d2fbac7328eda7b7a68348a4adcbb3a9961868b9 onward licensed under MPL-2.0
 */

package org.openintegrationengine.tlsmanager.shared;

public final class TLSPluginConstants {
    public static final String PLUGIN_POINTNAME = "TLS Manager";

    public static final String SETTINGS_TABNAME_MAIN = "TLS Settings";

    public static final String PROPERTY_TRUST_BACKEND = "trust.backend";

    public static final String ENV_PERSISTENCE_BACKEND = "OIE_TLS_PLUGIN_PERSISTENCE_BACKEND";
    public static final String ENV_PERSISTENCE_FS_TRUSTSTOREPATH = "OIE_TLS_PLUGIN_FS_TRUSTSTOREPATH";
    public static final String ENV_PERSISTENCE_FS_TRUSTSTOREPASS = "OIE_TLS_PLUGIN_FS_TRUSTSTOREPASS";
    public static final String ENV_PERSISTENCE_FS_KEYSTOREPASS = "OIE_TLS_PLUGIN_FS_KEYSTOREPASS";
    public static final String ENV_PERSISTENCE_FS_KEYSTOREPATH = "OIE_TLS_PLUGIN_FS_KEYSTOREPATH";

    public static final String ENV_SHOULD_DISABLE_UI = "OIE_TLS_PLUGIN_DISABLE_UI";

    public static final String PKCS12 = "PKCS12";

    // This ain't no joke
    public static final String TLS_SENDER_CONNECTOR_PROPERTIES_PLUGIN_POINT_NAME = "TLS Sender Connector Properties Plugin";
    public static final String TLS_LISTENER_CONNECTOR_PROPERTIES_PLUGIN_POINT_NAME = "TLS Listener Connector Properties Plugin";
    public static final String TLS_LISTENER_PROPERTIES_PLUGIN_POINT_NAME = "TLS Connector Properties Plugin";

    public static final String TLS_TASK_PLUGIN_POINT_NAME = "TLS Manager Tasks";

    private TLSPluginConstants() {}
}
