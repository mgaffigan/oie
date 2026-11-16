// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

import com.mirth.connect.plugins.ServerPlugin;

/**
 * Resolves the TLS configuration for a connector. Exactly one is active at a time.
 * <p>
 * The engine supplies {@code DefaultTlsContextProvider}. An extension that implements this
 * interface <em>replaces</em> it rather than adding to it, so connectors always have exactly one
 * code path and never a "with plugin" and "without plugin" branch. Installing two extensions that
 * both implement it is an error the server refuses to start with, because silently choosing one of
 * two security configurations is worse than not starting.
 * <p>
 * Nothing here names a transport. The role arrives on the request, taken from the connector's own
 * {@link TlsCapableConnectorPropertiesInterface}, and the configuration arrives as opaque plugin
 * properties, so an implementation works with connectors it has never heard of.
 *
 * <h2>Which method a transport calls</h2>
 * The two methods answer different questions, because transports differ in how TLS is selected:
 * <ul>
 * <li>Where TLS is a distinct socket or listener mode chosen by configuration -- every listener, and
 * the TCP sender -- the transport calls {@link #isTlsEnabled(TlsContextRequest)} first and only
 * calls {@link #createTlsContext(TlsContextRequest)} if it returns true. Returning false means a
 * plain socket.</li>
 * <li>Where TLS is selected per request by the URL scheme -- the HTTP and Web Service senders --
 * the transport calls {@link #createTlsContext(TlsContextRequest)} unconditionally, because an
 * {@code https} URL may be used at any time, and {@link #isTlsEnabled(TlsContextRequest)} is not
 * consulted.</li>
 * </ul>
 */
public interface TlsContextProvider extends ServerPlugin {

    /**
     * Whether this connector deployment should use TLS, for transports where that is a configured
     * mode rather than a property of the destination URL.
     */
    boolean isTlsEnabled(TlsContextRequest request);

    /**
     * Resolves the TLS configuration to use.
     *
     * @throws TlsConfigurationException
     *             if TLS is configured but cannot be satisfied. Callers let this fail the deploy;
     *             they must not fall back to an unencrypted connection.
     */
    TlsContext createTlsContext(TlsContextRequest request) throws TlsConfigurationException;
}
