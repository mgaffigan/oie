// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: 2025 NovaMap Health Limited <https://novamap.health>
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.tcp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

import org.apache.commons.lang3.StringUtils;
import org.openintegrationengine.engine.tls.TlsContext;
import org.openintegrationengine.engine.tls.TlsRole;

/**
 * The TLS counterpart of {@link StateAwareSocket}, used for both directions.
 * <p>
 * TLS is layered over this socket rather than replacing it, so every inherited accessor --
 * addresses, ports, timeouts, options -- keeps reporting the real connection. Only the streams and
 * {@link #close()} go through the {@link SSLSocket}.
 * <p>
 * As a client the handshake runs from {@link #connect(SocketAddress, int)}, after the plain
 * connection is established. As a server the socket is filled in by
 * {@link java.net.ServerSocket#implAccept(Socket)} and the handshake runs from
 * {@link #startTls(int)}, which is what lets one class serve both roles.
 */
public class StateAwareTlsSocket extends Socket implements StateAwareSocketInterface {

    private final TlsContext tlsContext;
    private final TlsRole role;

    private SSLSocket delegate;
    private BufferedInputStream bis;

    public StateAwareTlsSocket(TlsContext tlsContext, TlsRole role) {
        super();
        this.tlsContext = tlsContext;
        this.role = role;
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        connect(endpoint, 0);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (!(endpoint instanceof InetSocketAddress)) {
            throw new IOException("TLS connections require an InetSocketAddress, got " + (endpoint == null ? "null" : endpoint.getClass().getName()));
        }

        InetSocketAddress address = (InetSocketAddress) endpoint;

        // The plain connection first, then TLS layered on top of it.
        super.connect(endpoint, timeout);
        startTls(address.getHostString(), address.getPort(), 0);
    }

    /**
     * Runs the handshake on an already-connected socket, for the accepting side.
     *
     * @param handshakeTimeout
     *            how long to wait for the handshake, in milliseconds, or 0 to wait indefinitely.
     *            The socket's previous timeout is restored afterwards.
     */
    void startTls(int handshakeTimeout) throws IOException {
        startTls(getInetAddress() == null ? null : getInetAddress().getHostAddress(), getPort(), handshakeTimeout);
    }

    private void startTls(String peerHost, int peerPort, int handshakeTimeout) throws IOException {
        /*
         * autoClose is false so that closing the SSLSocket does not reach back into this socket;
         * close() below closes both, in order, without re-entering.
         */
        SSLSocket sslSocket = (SSLSocket) tlsContext.getSslContext().getSocketFactory().createSocket(this, peerHost, peerPort, false);
        sslSocket.setUseClientMode(role == TlsRole.CLIENT);

        SSLParameters parameters = sslSocket.getSSLParameters();
        tlsContext.applyTo(parameters, role);
        sslSocket.setSSLParameters(parameters);

        int previousTimeout = getSoTimeout();

        try {
            if (handshakeTimeout > 0) {
                setSoTimeout(handshakeTimeout);
            }

            sslSocket.startHandshake();
        } catch (IOException e) {
            try {
                sslSocket.close();
            } catch (IOException ignored) {
            }
            throw e;
        } finally {
            if (handshakeTimeout > 0 && !isClosed()) {
                setSoTimeout(previousTimeout);
            }
        }

        delegate = sslSocket;
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (bis == null) {
            bis = new BufferedInputStream(delegate == null ? super.getInputStream() : delegate.getInputStream());
        }

        return bis;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        return delegate == null ? super.getOutputStream() : delegate.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        try {
            if (delegate != null) {
                delegate.close();
            }
        } finally {
            super.close();
        }
    }

    /**
     * Peeks at the stream the same way {@link StateAwareSocket} does, so that keep-alive and
     * remote-host checks behave identically whether or not the connection is encrypted. Reading
     * through the {@link SSLSocket} means a close_notify is seen as end of stream rather than as
     * encrypted bytes.
     */
    @Override
    public boolean remoteSideHasClosed() throws IOException {
        if (isClosed()) {
            return true;
        }

        int oldTimeout;

        try {
            oldTimeout = getSoTimeout();
        } catch (IOException e) {
            if (StringUtils.containsIgnoreCase(e.getMessage(), "Socket Closed")) {
                return true;
            }
            throw e;
        }

        setSoTimeout(100);
        getInputStream().mark(1);

        try {
            return bis.read() == -1;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                bis.reset();
            } catch (IOException e) {
            }
            try {
                setSoTimeout(oldTimeout);
            } catch (SocketException e) {
            }
        }
    }
}
