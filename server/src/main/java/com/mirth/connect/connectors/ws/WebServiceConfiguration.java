// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.ws;

import java.util.Map;

import com.mirth.connect.donkey.server.channel.Connector;

/**
 * How the Web Service connectors build their transport. This is internal to the engine:
 * {@link DefaultWebServiceConfiguration} is the only implementation, and extensions influence TLS
 * through {@link org.openintegrationengine.engine.tls.TlsContextProvider} instead of by replacing
 * this.
 */
public interface WebServiceConfiguration {

    public void configureConnectorDeploy(Connector connector) throws Exception;

    public void configureConnectorUndeploy(Connector connector);

    public void configureReceiver(WebServiceReceiver connector) throws Exception;

    public void configureDispatcher(WebServiceDispatcher connector, WebServiceDispatcherProperties connectorProperties, Map<String, Object> requestContext) throws Exception;
}
