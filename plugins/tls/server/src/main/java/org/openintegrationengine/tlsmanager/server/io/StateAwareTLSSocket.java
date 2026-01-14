/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.io;

import com.mirth.connect.connectors.tcp.StateAwareSocketInterface;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;

import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;

@Slf4j
public class StateAwareTLSSocket extends Socket implements StateAwareSocketInterface {

    private final SSLConnectionSocketFactory socketFactory;
    private SSLSocket delegate;
    private boolean isClosing;

    public StateAwareTLSSocket(SSLConnectionSocketFactory socketFactory) {
        super();
        this.socketFactory = socketFactory;
        this.isClosing = false;
    }

    public StateAwareTLSSocket(SSLSocket delegate) {
        super();
        this.delegate = delegate;
        this.socketFactory = null;
        this.isClosing = false;
    }

    @Override
    public void connect(SocketAddress endpoint) throws IOException {
        this.connect(endpoint, 0);
    }

    @Override
    public void connect(SocketAddress endpoint, int timeout) throws IOException {
        if (endpoint instanceof InetSocketAddress inet) {

            try {
                // Perform the plain TCP connection first
                super.connect(endpoint, timeout);

                this.delegate = (SSLSocket) socketFactory.createLayeredSocket(
                    this,
                    inet.getHostString(),
                    inet.getPort(),
                    null
                );

                // If protocol is 1.3 read the stream to force completing the handshake
                if (this.delegate.getSession().getProtocol().equals("TLSv1.3")) {
                    //remoteSideHasClosedInternal(this.delegate);
                }
            } catch (SSLHandshakeException e) {
                log.warn("Failed to connect", e);
                throw e;
            }

        } else {
            throw new IOException("Expected InetSocketAddress for TLS connection");
        }
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (delegate != null) {
            return delegate.getInputStream();
        }

        return super.getInputStream();
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (delegate != null) {
            return delegate.getOutputStream();
        }
        return super.getOutputStream();
    }

    @Override
    public void close() throws IOException {
        log.debug("Closing TLS socket");
        if (isClosing) {
            log.debug("Prevented re-entry");
            // Prevent re-entry when sslSocket tries to close the underlying socket
            super.close();
            return;
        }

        isClosing = true;
        try {
            if (delegate != null) {
                log.debug("Closing delegate socket");
                delegate.close();
            } else {
                log.debug("Closing self socket");
                super.close();
            }
        } finally {
            log.debug("Resetting closing flag");
            isClosing = false;
        }
    }

    @Override
    public boolean remoteSideHasClosed() throws IOException {
        return remoteSideHasClosedInternal(
            Objects.requireNonNullElse(delegate, this)
        );
    }

    private boolean remoteSideHasClosedInternal(Socket socket) throws IOException {
        if (socket.isClosed()) return true;

        if (socket.isInputShutdown()) return true;

        int oldTimeout = socket.getSoTimeout();
        socket.setSoTimeout(200);

        var pbIn = new PushbackInputStream(socket.getInputStream());
        try {
            int b = pbIn.read();
            if (b == -1) return true;
            pbIn.unread(b);
            return false;
        } catch (SSLHandshakeException sslHandshakeException) {
            log.trace("SSL handshake failed", sslHandshakeException);
            throw sslHandshakeException;
        } finally {
            if (!socket.isClosed()) {
                socket.setSoTimeout(oldTimeout);
            }
        }
    }
}
