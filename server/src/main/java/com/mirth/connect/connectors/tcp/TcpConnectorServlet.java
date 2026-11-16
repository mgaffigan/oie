/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.connectors.tcp;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.apache.commons.lang3.math.NumberUtils;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.server.api.MirthServlet;
import org.openintegrationengine.engine.tls.TlsContext;
import org.openintegrationengine.engine.tls.TlsContextProvider;
import org.openintegrationengine.engine.tls.TlsContextRequest;

import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.util.ConnectorUtil;
import com.mirth.connect.server.util.TemplateValueReplacer;
import com.mirth.connect.util.ConnectionTestResponse;

public class TcpConnectorServlet extends MirthServlet implements TcpConnectorServletInterface {

    protected static final TemplateValueReplacer replacer = new TemplateValueReplacer();

    public TcpConnectorServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_POINT);
    }

    @Override
    public ConnectionTestResponse testConnection(String channelId, String channelName, TcpDispatcherProperties properties) {
        try {
            String host = replacer.replaceValues(properties.getRemoteAddress(), channelId, channelName);
            int port = NumberUtils.toInt(replacer.replaceValues(properties.getRemotePort(), channelId, channelName));
            int timeout = NumberUtils.toInt(replacer.replaceValues(properties.getResponseTimeout(), channelId, channelName));

            /*
             * Unlike HTTP there is no scheme to go on, so the configuration decides; this is the
             * same question the dispatcher asks when it creates its socket.
             */
            TlsContextProvider tlsContextProvider = ControllerFactory.getFactory().createExtensionController().getTlsContextProvider();
            TlsContextRequest tlsContextRequest = TlsContextRequest.forConnectorProperties(channelId, channelName, properties);
            TlsContext tlsContext = tlsContextProvider.isTlsEnabled(tlsContextRequest) ? tlsContextProvider.createTlsContext(tlsContextRequest) : null;

            String localAddr = null;
            int localPort = 0;

            if (properties.isOverrideLocalBinding()) {
                localAddr = replacer.replaceValues(properties.getLocalAddress(), channelId, channelName);
                localPort = NumberUtils.toInt(replacer.replaceValues(properties.getLocalPort(), channelId, channelName));
            }

            return ConnectorUtil.testConnection(host, port, timeout, localAddr, localPort, tlsContext);
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }
}