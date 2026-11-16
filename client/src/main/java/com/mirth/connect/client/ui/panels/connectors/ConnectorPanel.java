/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.ui.panels.connectors;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import net.miginfocom.swing.MigLayout;

import com.mirth.connect.client.ui.AbstractConnectorPropertiesPanel;
import com.mirth.connect.client.ui.ChannelSetup;
import com.mirth.connect.client.ui.ConnectorTypeDecoration;
import com.mirth.connect.client.ui.LoadedExtensions;
import com.mirth.connect.client.ui.UIConstants;
import com.mirth.connect.client.ui.VariableListHandler.TransferMode;
import com.mirth.connect.donkey.model.channel.ConnectorPluginProperties;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.model.channel.DestinationConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.channel.ListenerConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.channel.PollConnectorPropertiesInterface;
import com.mirth.connect.donkey.model.channel.SourceConnectorPropertiesInterface;
import com.mirth.connect.model.Connector.Mode;
import com.mirth.connect.model.InvalidConnectorPluginProperties;
import com.mirth.connect.model.MessageStorageMode;
import com.mirth.connect.plugins.ConnectorPropertiesPlugin;

import org.openintegrationengine.engine.tls.TlsCapableConnectorPropertiesInterface;
import org.openintegrationengine.engine.tls.TlsSettingsPanelProvider;

public class ConnectorPanel extends JPanel {
    private ChannelSetup channelSetup;
    private ConnectorSettingsPanel currentPanel;
    private JPanel connectorSettingsContainer;
    private ListenerSettingsPanel listenerSettingsPanel;
    private PollingSettingsPanel pollingSettingsPanel;
    private SourceSettingsPanel sourceSettingsPanel;
    private DestinationSettingsPanel destinationSettingsPanel;
    private Map<String, AbstractConnectorPropertiesPanel> connectorPropertiesPanels = new HashMap<String, AbstractConnectorPropertiesPanel>();

    public ConnectorPanel() {
        super(new MigLayout("insets 0, novisualpadding, hidemode 3, fill", "[grow]"));
        setBackground(UIConstants.BACKGROUND_COLOR);
        initComponents();

        for (AbstractConnectorPropertiesPanel connectorPropertiesPanel : connectorPropertiesPanels.values()) {
            connectorPropertiesPanel.setConnectorPanel(this);
        }
    }

    public void setChannelSetup(ChannelSetup channelSetup) {
        this.channelSetup = channelSetup;
        destinationSettingsPanel.setChannelSetup(channelSetup);
        sourceSettingsPanel.setChannelSetup(channelSetup);
    }

    public void setConnectorSettingsPanel(ConnectorSettingsPanel panel) {
        if (currentPanel != null) {
            connectorSettingsContainer.remove(currentPanel);
        }

        connectorSettingsContainer.add(panel);
        connectorSettingsContainer.revalidate();
        currentPanel = panel;
        currentPanel.setConnectorPanel(this);

        ((TitledBorder) connectorSettingsContainer.getBorder()).setTitle(panel.getConnectorName() + " Settings");

        ConnectorProperties connectorProperties = getConnectorSettingsPanel().getDefaults();
        pollingSettingsPanel.setVisible(connectorProperties instanceof PollConnectorPropertiesInterface);
        listenerSettingsPanel.setVisible(connectorProperties instanceof ListenerConnectorPropertiesInterface);
        sourceSettingsPanel.setVisible(connectorProperties instanceof SourceConnectorPropertiesInterface);
        destinationSettingsPanel.setVisible(connectorProperties instanceof DestinationConnectorPropertiesInterface);

        Set<String> activePanelNames = getActivePropertiesPanels(connectorProperties).keySet();

        for (Entry<String, AbstractConnectorPropertiesPanel> entry : connectorPropertiesPanels.entrySet()) {
            entry.getValue().setVisible(activePanelNames.contains(entry.getKey()));
        }
    }

    public SourceSettingsPanel getSourceSettingsPanel() {
        return sourceSettingsPanel;
    }

    public ConnectorSettingsPanel getConnectorSettingsPanel() {
        for (Component component : connectorSettingsContainer.getComponents()) {
            if (component.isVisible()) {
                return (ConnectorSettingsPanel) component;
            }
        }

        return null;
    }

    public Map<String, AbstractConnectorPropertiesPanel> getConnectorPluginPropertiesPanels() {
        return connectorPropertiesPanels;
    }

    public ConnectorProperties getProperties() {
        ConnectorSettingsPanel connectorSettingsPanel = getConnectorSettingsPanel();

        if (connectorSettingsPanel == null) {
            return null;
        }

        ConnectorProperties connectorProperties = connectorSettingsPanel.getProperties();

        if (connectorProperties instanceof PollConnectorPropertiesInterface) {
            pollingSettingsPanel.fillProperties((PollConnectorPropertiesInterface) connectorProperties);
        }

        if (connectorProperties instanceof ListenerConnectorPropertiesInterface) {
            listenerSettingsPanel.fillProperties((ListenerConnectorPropertiesInterface) connectorProperties);
        }

        if (connectorProperties instanceof SourceConnectorPropertiesInterface) {
            sourceSettingsPanel.fillProperties((SourceConnectorPropertiesInterface) connectorProperties);
        }

        if (connectorProperties instanceof DestinationConnectorPropertiesInterface) {
            destinationSettingsPanel.fillProperties((DestinationConnectorPropertiesInterface) connectorProperties);
        }

        if (connectorProperties != null) {
            Set<ConnectorPluginProperties> connectorPluginProperties = new HashSet<ConnectorPluginProperties>();

            for (AbstractConnectorPropertiesPanel connectorPropertiesPanel : getActivePropertiesPanels(connectorProperties).values()) {
                connectorPluginProperties.add(connectorPropertiesPanel.getProperties());
            }

            connectorProperties.setPluginProperties(connectorPluginProperties);
        }

        return connectorProperties;
    }

    public void setProperties(ConnectorProperties properties) {
        Mode mode = Mode.SOURCE;

        if (properties instanceof PollConnectorPropertiesInterface) {
            pollingSettingsPanel.resetInvalidProperties();
            pollingSettingsPanel.setProperties((PollConnectorPropertiesInterface) properties);
        }

        if (properties instanceof ListenerConnectorPropertiesInterface) {
            listenerSettingsPanel.resetInvalidProperties();
            listenerSettingsPanel.setProperties((ListenerConnectorPropertiesInterface) properties);
        }

        if (properties instanceof SourceConnectorPropertiesInterface) {
            sourceSettingsPanel.resetInvalidProperties();
            sourceSettingsPanel.setProperties((SourceConnectorPropertiesInterface) properties);
        }

        if (properties instanceof DestinationConnectorPropertiesInterface) {
            mode = Mode.DESTINATION;
            destinationSettingsPanel.resetInvalidProperties();
            destinationSettingsPanel.setProperties((DestinationConnectorPropertiesInterface) properties);
        }

        // Set the connector properties beforehand in case plugin properties need to reference them
        getConnectorSettingsPanel().resetInvalidProperties();
        getConnectorSettingsPanel().setProperties(properties);

        Set<String> addedPluginProperties = new HashSet<String>();

        // Set all properties existing in the model
        if (properties.getPluginProperties() != null) {
            for (ConnectorPluginProperties connectorPluginProperties : properties.getPluginProperties()) {
                if (!(connectorPluginProperties instanceof InvalidConnectorPluginProperties)) {
                    AbstractConnectorPropertiesPanel connectorPluginPropertiesPanel = connectorPropertiesPanels.get(connectorPluginProperties.getName());

                    if (connectorPluginPropertiesPanel != null) {
                        connectorPluginPropertiesPanel.resetInvalidProperties();
                        connectorPluginPropertiesPanel.setProperties(properties, connectorPluginProperties, mode, properties.getName());
                        addedPluginProperties.add(connectorPluginProperties.getName());
                    }
                }
            }
        }

        // For any supported plugin properties that weren't in the model, set the defaults
        for (Entry<String, AbstractConnectorPropertiesPanel> entry : getActivePropertiesPanels(properties).entrySet()) {
            if (!addedPluginProperties.contains(entry.getKey())) {
                AbstractConnectorPropertiesPanel connectorPluginPropertiesPanel = entry.getValue();
                connectorPluginPropertiesPanel.resetInvalidProperties();
                connectorPluginPropertiesPanel.setProperties(properties, connectorPluginPropertiesPanel.getDefaults(), mode, properties.getName());
            }
        }
    }

    public boolean checkProperties(ConnectorProperties properties, boolean highlight) {
        return checkProperties(getConnectorSettingsPanel(), properties, highlight);
    }

    public boolean checkProperties(ConnectorSettingsPanel connectorSettingsPanel, ConnectorProperties properties, boolean highlight) {
        boolean polling = true;
        Mode mode = Mode.SOURCE;

        if (properties instanceof PollConnectorPropertiesInterface) {
            pollingSettingsPanel.resetInvalidProperties();
            polling = pollingSettingsPanel.checkProperties((PollConnectorPropertiesInterface) properties, highlight);
        }

        boolean listener = true;

        if (properties instanceof ListenerConnectorPropertiesInterface) {
            listenerSettingsPanel.resetInvalidProperties();
            listener = listenerSettingsPanel.checkProperties((ListenerConnectorPropertiesInterface) properties, highlight);
        }

        boolean response = true;

        if (properties instanceof SourceConnectorPropertiesInterface) {
            sourceSettingsPanel.resetInvalidProperties();
            listener = sourceSettingsPanel.checkProperties((SourceConnectorPropertiesInterface) properties, highlight);
        }

        boolean destination = true;

        if (properties instanceof DestinationConnectorPropertiesInterface) {
            mode = Mode.DESTINATION;
            destinationSettingsPanel.resetInvalidProperties();
            destination = destinationSettingsPanel.checkProperties((DestinationConnectorPropertiesInterface) properties, highlight);
        }

        boolean pluginProperties = true;

        if (properties.getPluginProperties() != null) {
            for (ConnectorPluginProperties connectorPluginProperties : properties.getPluginProperties()) {
                if (!(connectorPluginProperties instanceof InvalidConnectorPluginProperties)) {
                    AbstractConnectorPropertiesPanel connectorPluginPropertiesPanel = connectorPropertiesPanels.get(connectorPluginProperties.getName());

                    if (connectorPluginPropertiesPanel != null) {
                        connectorPluginPropertiesPanel.resetInvalidProperties();
                        if (!connectorPluginPropertiesPanel.checkProperties(properties, connectorPluginProperties, mode, properties.getName(), highlight)) {
                            pluginProperties = false;
                        }
                    }
                }
            }
        }

        connectorSettingsPanel.resetInvalidProperties();
        boolean connector = connectorSettingsPanel.checkProperties(properties, highlight);

        return (connector && polling && listener && response && destination && pluginProperties);
    }

    public String doValidate(ConnectorProperties properties, boolean highlight) {
        return doValidate(getConnectorSettingsPanel(), properties, highlight);
    }

    public String doValidate(ConnectorSettingsPanel connectorSettingsPanel, ConnectorProperties properties, boolean highlight) {
        String error = null;

        if (!checkProperties(connectorSettingsPanel, properties, highlight)) {
            error = "Error in the form for connector \"" + connectorSettingsPanel.getConnectorName() + "\".\n\n";
        }

        String connectorError = connectorSettingsPanel.doValidate(properties, highlight);

        if (connectorError != null) {
            if (error == null) {
                error = "";
            }

            error += connectorError;
        }

        return error;
    }

    public void updateResponseDropDown() {
        if (getConnectorSettingsPanel().getProperties() instanceof SourceConnectorPropertiesInterface) {
            sourceSettingsPanel.updateResponseDropDown((SourceConnectorPropertiesInterface) getConnectorSettingsPanel().getProperties(), false);
        }
    }

    public ConnectorProperties getDefaults() {
        ConnectorProperties properties = getConnectorSettingsPanel().getDefaults();

        if (properties instanceof SourceConnectorPropertiesInterface) {
            if (((SourceConnectorPropertiesInterface) properties).getSourceConnectorProperties().getQueueBufferSize() == 0) {
                ((SourceConnectorPropertiesInterface) properties).getSourceConnectorProperties().setQueueBufferSize(channelSetup.defaultQueueBufferSize);
            }
        } else if (properties instanceof DestinationConnectorPropertiesInterface) {
            if (((DestinationConnectorPropertiesInterface) properties).getDestinationConnectorProperties().getQueueBufferSize() == 0) {
                ((DestinationConnectorPropertiesInterface) properties).getDestinationConnectorProperties().setQueueBufferSize(channelSetup.defaultQueueBufferSize);
            }
        }

        Set<ConnectorPluginProperties> connectorPluginProperties = new HashSet<ConnectorPluginProperties>();

        for (AbstractConnectorPropertiesPanel connectorPropertiesPanel : getActivePropertiesPanels(properties).values()) {
            connectorPluginProperties.add(connectorPropertiesPanel.getDefaults());
        }

        properties.setPluginProperties(connectorPluginProperties);

        return properties;
    }

    public TransferMode getTransferMode() {
        return getConnectorSettingsPanel().getTransferMode();
    }

    public String getRequiredInboundDataType() {
        return getConnectorSettingsPanel().getRequiredInboundDataType();
    }

    public String getRequiredOutboundDataType() {
        return getConnectorSettingsPanel().getRequiredOutboundDataType();
    }

    public String getInitialInboundDataType() {
        return getConnectorSettingsPanel().getInitialInboundDataType();
    }

    public String getInitialOutboundDataType() {
        return getConnectorSettingsPanel().getInitialOutboundDataType();
    }

    public String getInitialInboundResponseDataType() {
        return getConnectorSettingsPanel().getInitialInboundResponseDataType();
    }

    public String getInitialOutboundResponseDataType() {
        return getConnectorSettingsPanel().getInitialOutboundResponseDataType();
    }

    public void updatedField(String field) {
        getConnectorSettingsPanel().updatedField(field);

        /*
         * Forwarded on as well, because a plugin panel's content can depend on a field owned by the
         * connector: a TLS panel has to switch between the listening and connecting views when the
         * TCP mode changes, and nothing else would tell it.
         */
        for (AbstractConnectorPropertiesPanel connectorPropertiesPanel : getActivePropertiesPanels(getConnectorSettingsPanel().getDefaults()).values()) {
            connectorPropertiesPanel.updatedField(field);
        }
    }

    public void updateQueueWarning(MessageStorageMode messageStorageMode) {
        destinationSettingsPanel.updateQueueWarning(messageStorageMode);
        sourceSettingsPanel.updateQueueWarning(messageStorageMode);
    }

    public void updateNextFireTime() {
        pollingSettingsPanel.updateNextFireTime();
    }

    public void decorateConnectorType() {
        ConnectorTypeDecoration connectorTypeDecoration = currentPanel.getConnectorTypeDecoration();

        Map<String, AbstractConnectorPropertiesPanel> activePanels = getActivePropertiesPanels(currentPanel.getDefaults());

        // Give priority to connector property plugins for decoration
        for (AbstractConnectorPropertiesPanel connectorPropertiesPanel : activePanels.values()) {
            ConnectorTypeDecoration tempConnectorTypeDecoration = connectorPropertiesPanel.getConnectorTypeDecoration();
            if (tempConnectorTypeDecoration != null) {
                connectorTypeDecoration = tempConnectorTypeDecoration;
            }
        }

        channelSetup.decorateConnectorType(connectorTypeDecoration, getConnectorSettingsPanel().getDefaults() instanceof DestinationConnectorPropertiesInterface);

        currentPanel.doLocalDecoration(connectorTypeDecoration);
        for (AbstractConnectorPropertiesPanel connectorPropertiesPanel : activePanels.values()) {
            connectorPropertiesPanel.doLocalDecoration(connectorTypeDecoration);
        }
    }

    ResponseHandler getResponseHandler(final ResponseHandler delegate, final Method method) {
        return new ResponseHandler() {
            @Override
            public void handle(Object response) {
                boolean handleResponse = true;

                for (AbstractConnectorPropertiesPanel connectorPropertiesPanel : getActivePropertiesPanels(currentPanel.getDefaults()).values()) {
                    if (!connectorPropertiesPanel.handleConnectorServiceResponse(currentPanel, method, response)) {
                        handleResponse = false;
                    }
                }

                if (handleResponse && delegate != null) {
                    delegate.handle(response);
                }
            }
        };
    }

    /**
     * The properties panels that apply to a given connector, keyed the same way
     * {@code connectorPropertiesPanels} is.
     * <p>
     * Plugin panels claim connectors by name, whereas the TLS panel is mounted for any connector
     * whose properties declare TLS support. That is what lets a TLS extension work with connectors
     * it has no knowledge of.
     * <p>
     * The key is the plugin point name, which must equal the {@code getName()} of the properties
     * the panel produces -- {@link #setProperties(ConnectorProperties)} looks panels up by that
     * name when restoring saved settings, and a mismatch silently discards them.
     */
    private Map<String, AbstractConnectorPropertiesPanel> getActivePropertiesPanels(ConnectorProperties connectorProperties) {
        Map<String, AbstractConnectorPropertiesPanel> activePanels = new LinkedHashMap<String, AbstractConnectorPropertiesPanel>();

        if (connectorProperties == null) {
            return activePanels;
        }

        for (ConnectorPropertiesPlugin connectorPropertiesPlugin : LoadedExtensions.getInstance().getConnectorPropertiesPlugins().values()) {
            if (connectorPropertiesPlugin.isSupported(connectorProperties.getName())) {
                activePanels.put(connectorPropertiesPlugin.getPluginPointName(), connectorPropertiesPanels.get(connectorPropertiesPlugin.getPluginPointName()));
            }
        }

        TlsSettingsPanelProvider tlsSettingsPanelProvider = LoadedExtensions.getInstance().getTlsSettingsPanelProvider();

        if (tlsSettingsPanelProvider != null && connectorProperties instanceof TlsCapableConnectorPropertiesInterface) {
            activePanels.put(tlsSettingsPanelProvider.getPluginPointName(), connectorPropertiesPanels.get(tlsSettingsPanelProvider.getPluginPointName()));
        }

        return activePanels;
    }

    private void initComponents() {
        listenerSettingsPanel = new ListenerSettingsPanel();
        add(listenerSettingsPanel, "growx, wrap");

        pollingSettingsPanel = new PollingSettingsPanel(true);
        add(pollingSettingsPanel, "growx, wrap");

        sourceSettingsPanel = new SourceSettingsPanel();
        add(sourceSettingsPanel, "growx, wrap");

        destinationSettingsPanel = new DestinationSettingsPanel();
        add(destinationSettingsPanel, "growx, wrap");

        for (ConnectorPropertiesPlugin connectorPropertiesPlugin : LoadedExtensions.getInstance().getConnectorPropertiesPlugins().values()) {
            AbstractConnectorPropertiesPanel connectorPropertiesPanel = connectorPropertiesPlugin.getConnectorPropertiesPanel();
            connectorPropertiesPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), connectorPropertiesPlugin.getSettingsTitle(), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 1, 11)));
            connectorPropertiesPanels.put(connectorPropertiesPlugin.getPluginPointName(), connectorPropertiesPanel);
            add(connectorPropertiesPanel, "growx, wrap");
        }

        TlsSettingsPanelProvider tlsSettingsPanelProvider = LoadedExtensions.getInstance().getTlsSettingsPanelProvider();

        if (tlsSettingsPanelProvider != null) {
            AbstractConnectorPropertiesPanel tlsSettingsPanel = tlsSettingsPanelProvider.getTlsSettingsPanel();
            tlsSettingsPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), tlsSettingsPanelProvider.getSettingsTitle(), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 1, 11)));
            connectorPropertiesPanels.put(tlsSettingsPanelProvider.getPluginPointName(), tlsSettingsPanel);
            add(tlsSettingsPanel, "growx, wrap");
        }

        connectorSettingsContainer = new JPanel(new BorderLayout());
        connectorSettingsContainer.setBackground(new Color(255, 255, 255));
        connectorSettingsContainer.setBorder(BorderFactory.createTitledBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(204, 204, 204)), "Connector Settings", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, new Font("Tahoma", 1, 11)));
        add(connectorSettingsContainer, "grow, pushy");
    }
}