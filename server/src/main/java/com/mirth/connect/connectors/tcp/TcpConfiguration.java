// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import com.mirth.connect.donkey.server.channel.Connector;

/**
 * How the TCP connectors create their sockets. This is internal to the engine:
 * {@link DefaultTcpConfiguration} is the only implementation, and extensions influence TLS through
 * {@link org.openintegrationengine.engine.tls.TlsContextProvider} instead of by replacing this.
 * <p>
 * Keeping socket creation here rather than in an extension is deliberate. When this was an
 * extension point, an implementation had to remember to override every factory method, and missing
 * one -- {@link #createResponseSocket()} was the one missed in practice -- meant that traffic
 * quietly went out unencrypted on a connector the user had configured for TLS.
 */
public interface TcpConfiguration {

    public void configureConnectorDeploy(Connector connector) throws Exception;

    public ServerSocket createServerSocket(int port, int backlog) throws IOException;

    public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException;

    public Socket createSocket() throws IOException;

    public Socket createResponseSocket() throws IOException;

    public Map<String, Object> getSocketInformation(Socket socket);
}
