/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import lombok.Getter;

public enum RevocationMode implements DisplayTextEnum {
    DISABLED("Disabled"),
    SOFT_FAIL("Soft Fail"),
    HARD_FAIL("Hard Fail");

    @Getter
    private final String displayText;

    RevocationMode(String displayText) {
        this.displayText = displayText;
    }
}
