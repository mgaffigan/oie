// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.http;

import java.util.Map;

import javax.servlet.ServletRequest;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.openintegrationengine.engine.tls.TlsContext;

import com.mirth.connect.donkey.server.channel.Connector;

/**
 * How the HTTP connectors build their transport. This is internal to the engine:
 * {@link DefaultHttpConfiguration} is the only implementation, and extensions influence TLS through
 * {@link org.openintegrationengine.engine.tls.TlsContextProvider} instead of by replacing this.
 * <p>
 * It was previously an extension point in its own right, selected by class name from a database
 * property. That coupled extensions to Jetty and Apache HttpClient and to the engine's own
 * internals, which is how an extension could silently miss a newly added setting or a newly added
 * factory method and quietly fall back to an unencrypted socket.
 */
public interface HttpConfiguration {

    public void configureConnectorDeploy(Connector connector) throws Exception;

    public void configureConnectorUndeploy(Connector connector);

    public void configureReceiver(HttpReceiver connector) throws Exception;

    public void configureDispatcher(HttpDispatcher connector, HttpDispatcherProperties connectorProperties) throws Exception;

    /**
     * Registers the {@code https} socket factory built from {@code tlsContext}.
     *
     * @param tlsContext
     *            the resolved TLS configuration; never null.
     */
    public void configureSocketFactoryRegistry(TlsContext tlsContext, RegistryBuilder<ConnectionSocketFactory> registry) throws Exception;

    public Map<String, Object> getRequestInformation(ServletRequest request);
}
