// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 NovaMap Health Limited <https://novamap.health>
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import org.openintegrationengine.engine.tls.TlsContext;
import org.openintegrationengine.engine.tls.TlsRole;

/**
 * The TLS counterpart of {@link StateAwareServerSocket}.
 * <p>
 * This is an ordinary {@link ServerSocket} that accepts into a {@link StateAwareTlsSocket} and runs
 * the handshake before handing the connection on, rather than delegating to an
 * {@link javax.net.ssl.SSLServerSocket}. Accepting into our own socket type is what keeps
 * {@link StateAwareSocketInterface} working on TLS connections, so keep-alive and remote-host
 * checks behave the same encrypted or not.
 */
public class StateAwareTlsServerSocket extends ServerSocket {

    /**
     * How long a peer has to complete the handshake. Without this a client that connects and then
     * says nothing would occupy the accept loop indefinitely.
     */
    private static final int HANDSHAKE_TIMEOUT = 10000;

    private final TlsContext tlsContext;

    public StateAwareTlsServerSocket(int port, int backlog, TlsContext tlsContext) throws IOException {
        super(port, backlog);
        this.tlsContext = tlsContext;
    }

    public StateAwareTlsServerSocket(int port, int backlog, InetAddress bindAddr, TlsContext tlsContext) throws IOException {
        super(port, backlog, bindAddr);
        this.tlsContext = tlsContext;
    }

    @Override
    public Socket accept() throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }

        if (!isBound()) {
            throw new SocketException("Socket is not bound yet");
        }

        StateAwareTlsSocket socket = new StateAwareTlsSocket(tlsContext, TlsRole.SERVER);
        implAccept(socket);

        try {
            socket.startTls(HANDSHAKE_TIMEOUT);
        } catch (IOException e) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }

            throw e;
        }

        return socket;
    }
}
