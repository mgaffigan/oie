/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Set;

@Getter
@Setter
@XStreamAlias("trustedCertificate")
public class TrustedCertificate implements Serializable {

    public TrustedCertificate(String alias) {
        this.alias = alias;
    }

    private String alias;
    private String certificate;
    private Set<String> channelsInUse;
}
