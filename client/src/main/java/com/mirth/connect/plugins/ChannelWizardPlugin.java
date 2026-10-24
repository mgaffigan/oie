/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.plugins;

import java.util.Calendar;

import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.model.Channel;
import com.mirth.connect.model.Connector;
import com.mirth.connect.model.Connector.Mode;
import com.mirth.connect.model.Filter;
import com.mirth.connect.model.Transformer;

public abstract class ChannelWizardPlugin extends ClientPlugin {

    // HACK: Use reflection to avoid compile-time dependency on vm connector classes
    private static final String VM_RECEIVER_PROPERTIES_CLASS = "com.mirth.connect.connectors.vm.VmReceiverProperties";
    private static final String VM_DISPATCHER_PROPERTIES_CLASS = "com.mirth.connect.connectors.vm.VmDispatcherProperties";

    public ChannelWizardPlugin(String name) {
        super(name);
    }

    public abstract Channel runWizard();

    public Channel getDefaultNewChannel() {
        Channel channel = new Channel();

        channel.setName("New Channel");
        channel.getExportData().getMetadata().setLastModified(Calendar.getInstance());

        Connector sourceConnector = new Connector();
        sourceConnector.setEnabled(true);
        sourceConnector.setFilter(new Filter());
        Transformer sourceTransformer = new Transformer();
        sourceTransformer.setInboundDataType(UIConstants.DATATYPE_DEFAULT);
        sourceConnector.setTransformer(sourceTransformer);
        sourceConnector.setMode(Mode.SOURCE);
        sourceConnector.setName("sourceConnector");
        ConnectorProperties sourceConnectorProperties = createConnectorProperties(VM_RECEIVER_PROPERTIES_CLASS);
        sourceConnector.setTransportName(sourceConnectorProperties.getName());
        sourceConnector.setProperties(sourceConnectorProperties);
        channel.setSourceConnector(sourceConnector);

        Connector destinationConnector = new Connector();
        destinationConnector.setEnabled(true);
        destinationConnector.setFilter(new Filter());
        destinationConnector.setTransformer(new Transformer());
        destinationConnector.setResponseTransformer(new Transformer());
        destinationConnector.setMode(Mode.DESTINATION);
        destinationConnector.setName("Destination 1");
        ConnectorProperties destinationConnectorProperties = createConnectorProperties(VM_DISPATCHER_PROPERTIES_CLASS);
        destinationConnector.setTransportName(destinationConnectorProperties.getName());
        destinationConnector.setProperties(destinationConnectorProperties);
        channel.addDestination(destinationConnector);

        return channel;
    }

    private ConnectorProperties createConnectorProperties(String className) {
        try {
            return (ConnectorProperties) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create connector properties: " + className, e);
        }
    }
}
