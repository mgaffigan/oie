/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.properties;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openintegrationengine.tlsmanager.shared.TLSPluginConstants;

import java.util.Map;

@Getter
@Setter
@EqualsAndHashCode(callSuper = false)
@AllArgsConstructor
@ToString(callSuper = true)
public class TLSSenderProperties extends AbstractTLSConnectorProperties {

    private boolean isServerCertificateValidationEnabled;

    private boolean isHostnameVerificationEnabled;
    private String clientCertificateAlias;

    public TLSSenderProperties() {
        super();

        isServerCertificateValidationEnabled = false;

        isHostnameVerificationEnabled = true;
        clientCertificateAlias = null;
    }

    public TLSSenderProperties(TLSSenderProperties props) {
        super(props);

        isServerCertificateValidationEnabled = props.isServerCertificateValidationEnabled();

        isHostnameVerificationEnabled = props.isHostnameVerificationEnabled();
        clientCertificateAlias = props.getClientCertificateAlias();
    }

    @Override
    public String getName() {
        return TLSPluginConstants.TLS_SENDER_CONNECTOR_PROPERTIES_PLUGIN_POINT_NAME;
    }

    @Override
    public TLSSenderProperties clone() {
        return new TLSSenderProperties(this);
    }

    @Override
    public Map<String, Object> getPurgedProperties() {
        return Map.of();
    }
}
