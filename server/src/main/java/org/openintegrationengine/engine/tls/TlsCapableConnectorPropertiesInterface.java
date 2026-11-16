// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

/**
 * Implemented by the {@link com.mirth.connect.donkey.model.channel.ConnectorProperties} of a
 * connector that supports TLS. A connector opts in by implementing this interface; nothing else is
 * required of it and nothing keys off its name.
 * <p>
 * This is both the opt-in marker and the only source of the connector's TLS role, which is why no
 * part of the TLS extension point ever names a transport. A connector written outside this
 * repository gets a TLS settings panel in the Administrator and a resolved {@link TlsContext} at
 * deploy time purely by implementing this interface, with no change to the engine or to whichever
 * extension supplies the {@link TlsContextProvider}.
 * <p>
 * Implementations whose role is fixed return a constant; a connector that can run in either
 * direction, such as TCP, returns the role its current settings select.
 *
 * @see com.mirth.connect.donkey.model.channel.ListenerConnectorPropertiesInterface
 */
public interface TlsCapableConnectorPropertiesInterface {

    /**
     * The side of the handshake this connector deployment is on. Never null.
     */
    TlsRole getTlsRole();
}
