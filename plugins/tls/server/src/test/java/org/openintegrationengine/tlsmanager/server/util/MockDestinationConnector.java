/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.util;

import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.message.ConnectorMessage;
import com.mirth.connect.donkey.model.message.Response;
import com.mirth.connect.donkey.server.ConnectorTaskException;
import com.mirth.connect.donkey.server.channel.DestinationConnector;

public class MockDestinationConnector extends DestinationConnector {
    @Override
    public void replaceConnectorProperties(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) {

    }

    @Override
    public Response send(ConnectorProperties connectorProperties, ConnectorMessage connectorMessage) throws InterruptedException {
        return null;
    }

    @Override
    public void onDeploy() throws ConnectorTaskException {

    }

    @Override
    public void onUndeploy() throws ConnectorTaskException {

    }

    @Override
    public void onStart() throws ConnectorTaskException {

    }

    @Override
    public void onStop() throws ConnectorTaskException {

    }

    @Override
    public void onHalt() throws ConnectorTaskException {

    }
}
