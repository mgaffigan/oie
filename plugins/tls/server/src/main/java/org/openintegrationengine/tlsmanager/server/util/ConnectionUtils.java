/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.openintegrationengine.tlsmanager.shared.models.ConnectionTestResult;

import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.Arrays;

@Slf4j
public class ConnectionUtils {


    public static ConnectionTestResult testConnection(
        SSLConnectionSocketFactory socketFactory,
        String host,
        int port,
        int timeout,
        String localAddr,
        int localPort
    ) {
        return testConnection(
            socketFactory,
            null,
            host,
            port,
            timeout,
            localAddr,
            localPort
        );
    }

    public static ConnectionTestResult testConnection(
        SSLConnectionSocketFactory socketFactory,
        Socket socket,
        String host,
        int port,
        int timeout,
        String localAddr,
        int localPort
    ) {
        var startTime = Instant.now();

        if (
            host == null
            || host.isEmpty()
            || (port < 0)
            || (port > 65534)
        ) {
           return ConnectionTestResult.builder()
               .timestamp(startTime)
               .message("Invalid host or port")
               .build();
        }

        var target = HttpHost.create(host);

        InetSocketAddress remoteAddress = new InetSocketAddress(host, port);

        InetSocketAddress localAddress = null;
        if (localAddr != null) {
            if (
                localAddr.isEmpty()
                || localPort <= 0
                || localPort > 65535
            ) {
                return ConnectionTestResult.builder()
                    .timestamp(startTime)
                    .requestedAddress(host)
                    .message("Invalid local host or port")
                    .build();
            }

            localAddress = new InetSocketAddress(localAddr, localPort);
        }

        try (
            var sslSocket = (SSLSocket) socketFactory.connectSocket(
                timeout,
                socket,
                target,
                remoteAddress,
                localAddress,
                null
            )
        ) {
            var sess = sslSocket.getSession();

            if (log.isDebugEnabled()) {
                // Handshake is done if we got here. Inspect what happened:
                log.debug("Protocol: {}", sess.getProtocol());
                log.debug("Cipher:   {}", sess.getCipherSuite());
                log.debug("Peer:     {}", sess.getPeerPrincipal());

                // Did we actually present a client cert?
                var localCerts = sess.getLocalCertificates();     // null => none presented
                var localPrinc = sess.getLocalPrincipal();        // null => none presented
                log.debug("Client cert presented? {}", localPrinc != null);
            }

            //isSocketAlive(sslSocket);

            return ConnectionTestResult.builder()
                .success(true)
                .timestamp(startTime)
                .requestedAddress(host)
                .protocol(sess.getProtocol())
                .cipherSuite(sess.getCipherSuite())
                .sessionId(ConnectionTestResult.bytesToHex(sess.getId()))
                .peerHost(sess.getPeerHost())
                .peerPort(sess.getPeerPort())
                .sessionValid(sess.isValid())
                .sessionCreationTime(Instant.ofEpochMilli(sess.getCreationTime()))
                .sessionLastAccessedTime(Instant.ofEpochMilli(sess.getLastAccessedTime()))
                .supportedProtocols(Arrays.asList(sslSocket.getSupportedProtocols()))
                .enabledProtocols(Arrays.asList(sslSocket.getEnabledProtocols()))
                .supportedCipherSuites(Arrays.asList(sslSocket.getSupportedCipherSuites()))
                .enabledCipherSuites(Arrays.asList(sslSocket.getEnabledCipherSuites()))
                .certificates(Arrays.asList(sess.getPeerCertificates()))
                .chosenProtocol(sess.getProtocol())
                .chosenCipherSuite(sess.getCipherSuite())
                .build();

        } catch (Exception e) {
            log.error("Error connecting to host: {}", host, e);

            var result = ConnectionTestResult.builder()
                .success(false)
                .timestamp(startTime)
                .requestedAddress(host)
                .exceptionName(e.getClass().getCanonicalName())
                .exceptionMessage(e.getMessage());

            if (e.getCause() != null) {
                result.causeName(e.getCause().getClass().getCanonicalName());
                result.causeMessage(e.getCause().getMessage());
            }

            return result.build();
        }
    }

    /**
     * Performs a lightweight check to see if the given {@link Socket} appears alive.
     * <p>
     * This method verifies local socket state and performs a short read with a timeout
     * to detect EOF or I/O errors. It returns {@code true} if the connection seems open
     * and responsive, or {@code false} if it is closed, reset, or reaches end-of-stream.
     * <p>
     * Note that TCP cannot guarantee remote liveness without actual I/O, so this result
     * is best-effort only.
     *
     * @param socket the socket to test (not {@code null})
     */

    private static void isSocketAlive(Socket socket) throws IOException {
        log.trace("Checking socket liveness");
        int oldTimeOut = socket.getSoTimeout();
        socket.setSoTimeout(100); // 100ms read timeout

        log.trace("Set socket timeout to 100ms");

        var in = socket.getInputStream();

        try {
            if (in.available() > 0 || in.read() >= 0) {
                // Data received (or connection still healthy)
                log.debug("Socket alive (data or no EOF)");
            } else {
                // read() == -1 → remote closed cleanly
                log.debug("Socket dead (EOF)");
            }
        } catch (SocketTimeoutException e) {
            // no data within timeout → probably still open
            log.debug("Socket alive (idle)");
            throw e;
        } catch (IOException e) {
            // network error, RST, etc.
            log.debug("Socket dead (idle)", e);
            throw e;
        } finally {
            if (!socket.isClosed()) {
                socket.setSoTimeout(oldTimeOut);
            }
        }
    }
}
