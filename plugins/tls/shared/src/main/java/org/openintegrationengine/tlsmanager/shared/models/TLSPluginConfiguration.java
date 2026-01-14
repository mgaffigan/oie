/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.openintegrationengine.tlsmanager.shared.PersistenceMode;
import org.openintegrationengine.tlsmanager.shared.TLSPluginConstants;

import static org.openintegrationengine.tlsmanager.shared.TLSPluginConstants.ENV_PERSISTENCE_FS_KEYSTOREPASS;
import static org.openintegrationengine.tlsmanager.shared.TLSPluginConstants.ENV_PERSISTENCE_FS_KEYSTOREPATH;
import static org.openintegrationengine.tlsmanager.shared.TLSPluginConstants.ENV_PERSISTENCE_FS_TRUSTSTOREPASS;
import static org.openintegrationengine.tlsmanager.shared.TLSPluginConstants.ENV_PERSISTENCE_FS_TRUSTSTOREPATH;
import static org.openintegrationengine.tlsmanager.shared.TLSPluginConstants.ENV_SHOULD_DISABLE_UI;

@Slf4j
public record TLSPluginConfiguration(
    PersistenceMode persistenceMode,
    String truststorePath,
    String truststorePassword,
    String keystorePath,
    String keystorePassword,
    boolean disableUI
) {
    public static TLSPluginConfiguration fromEnv() {
        var conf = new TLSPluginConfiguration(
            getPersistenceMode(),
            readKeyFromEnv(ENV_PERSISTENCE_FS_TRUSTSTOREPATH, false),
            readKeyFromEnv(ENV_PERSISTENCE_FS_TRUSTSTOREPASS, false),
            readKeyFromEnv(ENV_PERSISTENCE_FS_KEYSTOREPATH, false),
            readKeyFromEnv(ENV_PERSISTENCE_FS_KEYSTOREPASS, false),
            Boolean.parseBoolean(readKeyFromEnv(ENV_SHOULD_DISABLE_UI, false))
        );

        log.debug("Using configuration: {}", conf);

        return conf;
    }

    private static PersistenceMode getPersistenceMode() {
        var persistenceModeFromEnv = readKeyFromEnv(TLSPluginConstants.ENV_PERSISTENCE_BACKEND, false);

        PersistenceMode persistenceMode;
        if (persistenceModeFromEnv == null) {
            log.debug("No persistence mode environment variable set, defaulting to \"database\"");
            persistenceMode = PersistenceMode.DATABASE;
        } else {
            persistenceMode = PersistenceMode.valueOf(persistenceModeFromEnv.toUpperCase());
        }

        log.info("Using persistence mode {}", persistenceMode);

        return persistenceMode;
    }

    private static String readKeyFromEnv(String key, boolean isRequired) {
        var keyFromEnv = System.getenv(key);
        if (keyFromEnv == null && isRequired) {
            throw new IllegalStateException("Environment variable (%s) is not set".formatted(key));
        }

        return keyFromEnv;
    }

    @NotNull
    @Override
    public String toString() {
        return "%s[persistenceMode=%s, truststorePath=%s, truststorePassword=%s, keystorePath=%s, keystorePassword=%s, disableUI=%s]".formatted(
            this.getClass().getSimpleName(),
            persistenceMode,
            truststorePath,
            anonymize(truststorePassword),
            keystorePath,
            anonymize(keystorePassword),
            disableUI
        );
    }

    public static String anonymize(String input) {
        if (input == null || input.length() <= 2) {
            return input; // too short to anonymize meaningfully
        }
        return input.charAt(0) + "***" + input.charAt(input.length() - 1);
    }
}
