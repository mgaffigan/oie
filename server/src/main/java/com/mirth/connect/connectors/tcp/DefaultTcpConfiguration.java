// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import org.openintegrationengine.engine.tls.TlsContext;
import org.openintegrationengine.engine.tls.TlsContextProvider;
import org.openintegrationengine.engine.tls.TlsContextRequest;
import org.openintegrationengine.engine.tls.TlsRole;

import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.server.controllers.ControllerFactory;

public class DefaultTcpConfiguration implements TcpConfiguration {

    /** The connector's own direction. Null when TLS is not configured, which means plain sockets. */
    private TlsContext tlsContext;

    /**
     * Resolved separately because a listener that returns responses on a new connection accepts as
     * a server but connects out as a client, and the two directions are configured independently.
     */
    private TlsContext responseTlsContext;

    @Override
    public void configureConnectorDeploy(Connector connector) throws Exception {
        TlsContextProvider tlsContextProvider = ControllerFactory.getFactory().createExtensionController().getTlsContextProvider();

        TlsContextRequest request = TlsContextRequest.forConnector(connector);
        tlsContext = tlsContextProvider.isTlsEnabled(request) ? tlsContextProvider.createTlsContext(request) : null;

        if (request.getRole() == TlsRole.CLIENT) {
            responseTlsContext = tlsContext;
        } else {
            TlsContextRequest responseRequest = TlsContextRequest.forConnector(connector, TlsRole.CLIENT);
            responseTlsContext = tlsContextProvider.isTlsEnabled(responseRequest) ? tlsContextProvider.createTlsContext(responseRequest) : null;
        }
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
        if (tlsContext == null) {
            return new StateAwareServerSocket(port, backlog);
        }

        return new StateAwareTlsServerSocket(port, backlog, tlsContext);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
        if (tlsContext == null) {
            return new StateAwareServerSocket(port, backlog, bindAddr);
        }

        return new StateAwareTlsServerSocket(port, backlog, bindAddr, tlsContext);
    }

    @Override
    public Socket createSocket() {
        if (tlsContext == null) {
            return new StateAwareSocket();
        }

        return new StateAwareTlsSocket(tlsContext, TlsRole.CLIENT);
    }

    @Override
    public Socket createResponseSocket() {
        if (responseTlsContext == null) {
            return new StateAwareSocket();
        }

        return new StateAwareTlsSocket(responseTlsContext, TlsRole.CLIENT);
    }

    @Override
    public Map<String, Object> getSocketInformation(Socket socket) {
        return new HashMap<String, Object>();
    }
}
