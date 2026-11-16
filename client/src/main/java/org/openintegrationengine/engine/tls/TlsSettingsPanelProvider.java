// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

import com.mirth.connect.client.ui.AbstractConnectorPropertiesPanel;
import com.mirth.connect.plugins.ClientPlugin;

/**
 * Supplies the TLS settings panel shown in the Administrator for every connector that supports TLS.
 * This is the client-side counterpart of
 * {@link org.openintegrationengine.engine.tls.TlsContextProvider}, and the two are implemented
 * together: whichever extension resolves TLS on the server is the one that collects the settings
 * it reads.
 * <p>
 * Exactly one is active, which is why this is not a
 * {@link com.mirth.connect.plugins.ConnectorPropertiesPlugin}. Those stack -- every one that claims
 * a connector adds its own titled section -- whereas a TLS panel must <em>replace</em> whatever
 * came before it rather than appear beside it.
 * <p>
 * The provider does not say which connectors it applies to. The panel is mounted for any connector
 * whose properties implement
 * {@link org.openintegrationengine.engine.tls.TlsCapableConnectorPropertiesInterface}, so an
 * implementation needs no knowledge of connectors and automatically covers ones it has never heard
 * of.
 * <p>
 * The engine ships no implementation, because the TLS it provides on its own has nothing to
 * configure: trust comes from the JVM, and no identity is presented. When no extension supplies
 * one, connectors simply show no TLS section.
 */
public abstract class TlsSettingsPanelProvider extends ClientPlugin {

    public TlsSettingsPanelProvider(String pluginName) {
        super(pluginName);
    }

    /**
     * The title for the panel's border, for example "TLS Settings".
     */
    public abstract String getSettingsTitle();

    /**
     * The panel itself. Called once; the instance is reused across every connector, so it must
     * reset itself fully on each
     * {@link AbstractConnectorPropertiesPanel#setProperties(com.mirth.connect.donkey.model.channel.ConnectorProperties, com.mirth.connect.donkey.model.channel.ConnectorPluginProperties, com.mirth.connect.client.ui.panels.connectors.ConnectorPanel.Mode, String)}
     * rather than carrying state between connectors.
     */
    public abstract AbstractConnectorPropertiesPanel getTlsSettingsPanel();

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void reset() {}
}
