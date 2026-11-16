// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

import java.util.Collections;
import java.util.Set;

import com.mirth.connect.donkey.model.channel.ConnectorPluginProperties;
import com.mirth.connect.donkey.model.channel.ConnectorProperties;
import com.mirth.connect.donkey.server.channel.Connector;

/**
 * Everything a {@link TlsContextProvider} is given in order to resolve a {@link TlsContext}.
 * <p>
 * This is deliberately not a {@link Connector}. TLS is also needed in places where no connector has
 * been deployed -- validating an OAuth2 token endpoint, testing a connection from the Administrator,
 * and fetching a WSDL at design time -- and all of those already hold the connector's properties.
 * Keying the extension point on properties rather than on a deployed connector lets one
 * implementation serve every caller.
 * <p>
 * {@link #getPluginProperties()} is the opaque payload: the engine never inspects it and never
 * names any type inside it. An implementation locates its own entry and may keep the whole
 * configuration there, a reference to configuration held elsewhere, or a mixture of the two.
 */
public final class TlsContextRequest {

    private final String channelId;
    private final String channelName;
    private final String transportName;
    private final TlsRole role;
    private final ConnectorProperties connectorProperties;
    private final Set<ConnectorPluginProperties> pluginProperties;

    private TlsContextRequest(String channelId, String channelName, String transportName, TlsRole role,
            ConnectorProperties connectorProperties, Set<ConnectorPluginProperties> pluginProperties) {
        this.channelId = channelId;
        this.channelName = channelName;
        this.transportName = transportName;
        this.role = role;
        this.connectorProperties = connectorProperties;
        this.pluginProperties = pluginProperties == null ? Collections.<ConnectorPluginProperties> emptySet()
                : Collections.unmodifiableSet(pluginProperties);
    }

    /**
     * For a connector being deployed. The role comes from the connector's own properties, so the
     * caller does not decide it.
     *
     * @throws TlsConfigurationException
     *             if the connector's properties do not implement
     *             {@link TlsCapableConnectorPropertiesInterface}, meaning the connector has not
     *             opted in to TLS and should never have asked.
     */
    public static TlsContextRequest forConnector(Connector connector) throws TlsConfigurationException {
        return forConnector(connector, null);
    }

    /**
     * As {@link #forConnector(Connector)}, but for a connection whose role is not the connector's
     * own. A TCP listener that returns responses on a new connection is the case this exists for:
     * it accepts as a server but connects out as a client, and the two directions are configured
     * separately.
     *
     * @param role
     *            the role to resolve for, or null to use the connector's own.
     */
    public static TlsContextRequest forConnector(Connector connector, TlsRole role) throws TlsConfigurationException {
        ConnectorProperties properties = connector.getConnectorProperties();
        String channelName = connector.getChannel() == null ? null : connector.getChannel().getName();
        return forConnectorProperties(connector.getChannelId(), channelName, properties, role);
    }

    /**
     * For a caller that holds a connector's properties but has no deployed connector, such as a
     * connection test or a design-time WSDL fetch. The role comes from the properties.
     *
     * @throws TlsConfigurationException
     *             if the properties do not implement {@link TlsCapableConnectorPropertiesInterface}.
     */
    public static TlsContextRequest forConnectorProperties(String channelId, String channelName,
            ConnectorProperties properties) throws TlsConfigurationException {
        return forConnectorProperties(channelId, channelName, properties, null);
    }

    /**
     * As {@link #forConnectorProperties(String, String, ConnectorProperties)}, but for a connection
     * whose role is not the connector's own.
     *
     * @param role
     *            the role to resolve for, or null to use the connector's own.
     */
    public static TlsContextRequest forConnectorProperties(String channelId, String channelName,
            ConnectorProperties properties, TlsRole role) throws TlsConfigurationException {
        if (!(properties instanceof TlsCapableConnectorPropertiesInterface)) {
            throw new TlsConfigurationException(properties.getName() + " does not support TLS.");
        }

        if (role == null) {
            role = ((TlsCapableConnectorPropertiesInterface) properties).getTlsRole();
        }

        return new TlsContextRequest(channelId, channelName, properties.getName(), role, properties,
                properties.getPluginProperties());
    }

    /**
     * For a caller that has neither a connector nor connector properties, only a set of plugin
     * properties and a known role -- currently the OAuth2 token endpoint, whose settings hang off
     * the authenticator rather than off a connector.
     */
    public static TlsContextRequest forPluginProperties(String channelId, String channelName, String transportName,
            TlsRole role, Set<ConnectorPluginProperties> pluginProperties) {
        return new TlsContextRequest(channelId, channelName, transportName, role, null, pluginProperties);
    }

    public String getChannelId() {
        return channelId;
    }

    /** May be null when the caller has no deployed channel. */
    public String getChannelName() {
        return channelName;
    }

    /** The connector's display name, for example "HTTP Sender". May be null for callers with no connector. */
    public String getTransportName() {
        return transportName;
    }

    public TlsRole getRole() {
        return role;
    }

    /** May be null when the caller has no connector properties. */
    public ConnectorProperties getConnectorProperties() {
        return connectorProperties;
    }

    /** Never null; possibly empty. Unmodifiable. */
    public Set<ConnectorPluginProperties> getPluginProperties() {
        return pluginProperties;
    }
}
