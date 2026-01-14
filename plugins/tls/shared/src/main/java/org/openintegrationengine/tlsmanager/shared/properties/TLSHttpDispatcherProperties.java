/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.properties;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorPropertiesInterface;
import com.mirth.connect.donkey.util.DonkeyElement;

public class TLSHttpDispatcherProperties extends ConnectorProperties implements DestinationConnectorPropertiesInterface {

    private DestinationConnectorProperties destinationConnectorProperties;

    public TLSHttpDispatcherProperties() {
        destinationConnectorProperties = new DestinationConnectorProperties(true);
    }

    public TLSHttpDispatcherProperties(TLSHttpDispatcherProperties properties) {
        super(properties);
        destinationConnectorProperties = new DestinationConnectorProperties(true);
    }

    @Override
    public String getProtocol() {
        return "HTTP";
    }

    @Override
    public String getName() {
        return "HTTP Sender";
    }

    @Override
    public String toFormattedString() {
        return "";
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public DestinationConnectorProperties getDestinationConnectorProperties() {
        return destinationConnectorProperties;
    }

    @Override
    public boolean canValidateResponse() {
        return false;
    }

    @Override
    public ConnectorProperties clone() {
        return new TLSHttpDispatcherProperties(this);
    }

    @Override
    public void migrate3_0_1(DonkeyElement donkeyElement) { }

    @Override
    public void migrate3_0_2(DonkeyElement donkeyElement) { }
}
