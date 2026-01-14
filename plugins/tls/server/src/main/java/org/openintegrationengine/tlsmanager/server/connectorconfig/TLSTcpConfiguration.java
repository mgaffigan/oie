/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.connectorconfig;

import com.mirth.connect.connectors.tcp.DefaultTcpConfiguration;
import com.mirth.connect.connectors.tcp.StateAwareServerSocket;
import com.mirth.connect.connectors.tcp.StateAwareSocket;
import com.mirth.connect.connectors.tcp.TcpDispatcher;
import com.mirth.connect.connectors.tcp.TcpDispatcherProperties;
import com.mirth.connect.connectors.tcp.TcpReceiver;
import com.mirth.connect.connectors.tcp.TcpReceiverProperties;
import com.mirth.connect.donkey.server.channel.Connector;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.openintegrationengine.tlsmanager.server.SocketFactoryService;
import org.openintegrationengine.tlsmanager.server.TLSServicePlugin;
import org.openintegrationengine.tlsmanager.server.io.StateAwareTLSServerSocket;
import org.openintegrationengine.tlsmanager.server.io.StateAwareTLSSocket;
import org.openintegrationengine.tlsmanager.shared.properties.TLSConnectorProperties;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

@Slf4j
public class TLSTcpConfiguration extends DefaultTcpConfiguration {

    private final SocketFactoryService socketFactoryService;

    private TLSConnectorProperties tlsConnectorProperties;

    private SSLConnectionSocketFactory socketFactory;

    public TLSTcpConfiguration() {
        this(TLSServicePlugin.getPluginInstance().getSocketFactoryService());
    }

    public TLSTcpConfiguration(SocketFactoryService socketFactoryService) {
        this.socketFactoryService = socketFactoryService;
    }

    @Override
    public void configureConnectorDeploy(Connector connector) throws Exception {
        boolean isServerMode;

        if (connector instanceof TcpDispatcher tcpDispatcher) {

            var dispatcherProperties = (TcpDispatcherProperties) tcpDispatcher.getConnectorProperties();
            isServerMode = dispatcherProperties.isServerMode();


        } else if (connector instanceof TcpReceiver tcpReceiver) {
            var receiverProperties = (TcpReceiverProperties) tcpReceiver.getConnectorProperties();
            isServerMode = receiverProperties.isServerMode();
        } else {
            // should not get here
            throw new IllegalStateException("Unexpected connector type: %s".formatted(connector.getClass().getCanonicalName()));
        }

        this.tlsConnectorProperties = getConnectorProperties(TLSConnectorProperties.class, connector);

        if (!isServerMode) {
            if (tlsConnectorProperties != null && tlsConnectorProperties.isTlsManagerEnabled()) {
                socketFactory = socketFactoryService.getConnectorSocketFactory(tlsConnectorProperties);
            }
        }
    }

    @Override
    public Socket createSocket() {
        if (tlsConnectorProperties == null || !tlsConnectorProperties.isTlsManagerEnabled()) {
            return new StateAwareSocket();
        } else {
            if (socketFactory == null) {
                throw new IllegalStateException("TLS for TCP connections is enabled, but socket factory is null. Possibly because no trust anchors were found.");
            }

            return new StateAwareTLSSocket(socketFactory);
        }
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog) throws IOException {
        log.error("Unexpected call to createServerSocket(int, int)");
        return super.createServerSocket(port, backlog);
    }

    @Override
    public ServerSocket createServerSocket(int port, int backlog, InetAddress bindAddr) throws IOException {
        var createTlsSocket = tlsConnectorProperties != null && tlsConnectorProperties.isTlsManagerEnabled();

        log.debug(
            "Creating server socket. Properties null - {}; Manager enabled - {}",
            tlsConnectorProperties == null,
            createTlsSocket
        );

        if (createTlsSocket) {
            var contextContainer = socketFactoryService.generateTLSContext(tlsConnectorProperties);
            return new StateAwareTLSServerSocket(port, backlog, bindAddr, contextContainer);
        } else {
            return new StateAwareServerSocket(port, backlog, bindAddr);
        }
    }

    private <T> T getConnectorProperties(Class<T> propertiesClass, Connector connector) {
        return connector.getConnectorProperties().getPluginProperties()
            .stream()
            .filter(propertiesClass::isInstance)
            .findFirst()
            .map(propertiesClass::cast)
            .orElse(null);
    }
}
