// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation

package com.mirth.connect.server.util;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;

import org.apache.commons.lang3.StringUtils;
import org.openintegrationengine.engine.tls.TlsContext;
import org.openintegrationengine.engine.tls.TlsRole;

import com.mirth.connect.util.ConnectionTestResponse;

public class ConnectorUtil {
    public static ConnectionTestResponse testConnection(String host, int port, int timeout) throws Exception {
        return testConnection(host, port, timeout, null, 0);
    }

    public static ConnectionTestResponse testConnection(String host, int port, int timeout, String localAddr) throws Exception {
        return testConnection(host, port, timeout, localAddr, 0);
    }

    public static ConnectionTestResponse testConnection(String host, int port, int timeout, String localAddr, int localPort) throws Exception {
        return testConnection(host, port, timeout, localAddr, localPort, null);
    }

    /**
     * Tests reachability, and when {@code tlsContext} is supplied also completes a TLS handshake so
     * that the result reflects whether the two sides can actually agree -- certificate, trust,
     * protocol and cipher -- rather than only whether the port accepts connections.
     *
     * @param tlsContext
     *            the TLS configuration to connect with, or null to test the plain connection only.
     */
    public static ConnectionTestResponse testConnection(String host, int port, int timeout, String localAddr, int localPort, TlsContext tlsContext) throws Exception {
        Socket socket = null;
        InetSocketAddress address = null;
        InetSocketAddress localAddress = null;

        try {
            address = new InetSocketAddress(host, port);

            if (StringUtils.isBlank(address.getAddress().getHostAddress()) || (address.getPort() < 0) || (address.getPort() > 65534)) {
                throw new Exception();
            }
        } catch (Exception e) {
            return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Invalid host or port.");
        }

        if (localAddr != null) {
            try {
                localAddress = new InetSocketAddress(localAddr, localPort);

                if (StringUtils.isBlank(localAddress.getAddress().getHostAddress()) || (localAddress.getPort() < 0) || (localAddress.getPort() > 65534)) {
                    throw new Exception();
                }
            } catch (Exception e) {
                return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Invalid local host or port.");
            }
        }

        try {
            socket = new Socket();

            if (localAddress != null) {
                try {
                    socket.bind(localAddress);
                } catch (Exception e) {
                    return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Could not bind to local address: " + localAddress.getAddress().getHostAddress() + ":" + localAddress.getPort());
                }
            }

            socket.connect(address, timeout);
            String connectionInfo = socket.getLocalAddress().getHostAddress() + ":" + socket.getLocalPort() + " -> " + address.getAddress().getHostAddress() + ":" + address.getPort();

            if (tlsContext == null) {
                return new ConnectionTestResponse(ConnectionTestResponse.Type.SUCCESS, "Successfully connected to host: " + connectionInfo, connectionInfo);
            }

            SSLSocket sslSocket = (SSLSocket) tlsContext.getSslContext().getSocketFactory().createSocket(socket, host, port, true);
            // The layered socket now owns the connection, so the finally block closes that instead.
            socket = sslSocket;

            SSLParameters parameters = sslSocket.getSSLParameters();
            tlsContext.applyTo(parameters, TlsRole.CLIENT);
            sslSocket.setSSLParameters(parameters);
            sslSocket.setSoTimeout(timeout);
            sslSocket.startHandshake();

            SSLSession session = sslSocket.getSession();
            ConnectionTestResponse response = new ConnectionTestResponse(ConnectionTestResponse.Type.SUCCESS, "Successfully connected to host over TLS: " + connectionInfo, connectionInfo);
            response.setTlsProtocol(session.getProtocol());
            response.setTlsCipherSuite(session.getCipherSuite());
            response.setPeerCertificateSubjects(getPeerCertificateSubjects(session));
            return response;
        } catch (SocketTimeoutException ste) {
            return new ConnectionTestResponse(ConnectionTestResponse.Type.TIME_OUT, "Timed out connecting to host: " + address.getAddress().getHostAddress() + ":" + address.getPort());
        } catch (javax.net.ssl.SSLException e) {
            return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Connected to host but the TLS handshake failed: " + address.getAddress().getHostAddress() + ":" + address.getPort() + " (" + e.getMessage() + ")");
        } catch (Exception e) {
            return new ConnectionTestResponse(ConnectionTestResponse.Type.FAILURE, "Could not connect to host: " + address.getAddress().getHostAddress() + ":" + address.getPort());
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    private static List<String> getPeerCertificateSubjects(SSLSession session) {
        List<String> subjects = new ArrayList<String>();

        try {
            for (Certificate certificate : session.getPeerCertificates()) {
                if (certificate instanceof X509Certificate) {
                    subjects.add(((X509Certificate) certificate).getSubjectX500Principal().getName());
                } else {
                    subjects.add(certificate.getType());
                }
            }
        } catch (Exception e) {
            // An anonymous or resumed session may expose no chain; the rest of the result stands.
        }

        return subjects;
    }
}
