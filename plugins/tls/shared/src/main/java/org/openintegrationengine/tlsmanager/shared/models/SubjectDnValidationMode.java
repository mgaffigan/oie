/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import lombok.Getter;

public enum SubjectDnValidationMode implements DisplayTextEnum {
    NONE("None"),
    PARTIAL("Partial"),
    EXACT("Exact");

    @Getter
    private final String displayText;

    SubjectDnValidationMode(String displayText) {
        this.displayText = displayText;
    }
}
