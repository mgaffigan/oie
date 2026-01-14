/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@XStreamAlias("localCertificate")
public class LocalCertificate extends TrustedCertificate implements Serializable {

    public LocalCertificate(String alias) {
        super(alias);
    }

    private String key;
}
