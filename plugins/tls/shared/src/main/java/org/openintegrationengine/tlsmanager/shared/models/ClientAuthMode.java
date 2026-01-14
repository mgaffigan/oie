/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import lombok.Getter;

public enum ClientAuthMode implements DisplayTextEnum {
    NONE("None"),
    REQUESTED("Requested"),
    REQUIRED("Required");

    @Getter
    private final String displayText;

    ClientAuthMode(String displayText) {
        this.displayText = displayText;
    }
}
