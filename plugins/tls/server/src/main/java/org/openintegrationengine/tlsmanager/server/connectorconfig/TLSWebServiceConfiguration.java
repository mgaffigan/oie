/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.connectorconfig;

import com.mirth.connect.connectors.ws.DefaultWebServiceConfiguration;
import com.mirth.connect.connectors.ws.SSLSocketFactoryWrapper;
import com.mirth.connect.connectors.ws.WebServiceDispatcher;
import com.mirth.connect.connectors.ws.WebServiceDispatcherProperties;
import com.mirth.connect.connectors.ws.WebServiceReceiver;
import com.mirth.connect.donkey.server.channel.Connector;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import lombok.extern.slf4j.Slf4j;
import org.openintegrationengine.tlsmanager.server.SocketFactoryService;
import org.openintegrationengine.tlsmanager.server.TLSServicePlugin;
import org.openintegrationengine.tlsmanager.shared.models.ClientAuthMode;
import org.openintegrationengine.tlsmanager.shared.models.WeirdIntermediaryContextContainer;
import org.openintegrationengine.tlsmanager.shared.models.WeirdIntermediaryListenerContextContainer;
import org.openintegrationengine.tlsmanager.shared.properties.TLSConnectorProperties;

import javax.net.ssl.SSLSocketFactory;
import java.util.Map;

@Slf4j
public class TLSWebServiceConfiguration extends DefaultWebServiceConfiguration {

    private final SocketFactoryService socketFactoryService;

    private WeirdIntermediaryContextContainer senderContainer;
    private WeirdIntermediaryListenerContextContainer listenerContainer;

    public TLSWebServiceConfiguration() {
        // This looks ugly, I know
        this(TLSServicePlugin.getPluginInstance().getSocketFactoryService());
    }

    public TLSWebServiceConfiguration(SocketFactoryService socketFactoryService) {
        this.socketFactoryService = socketFactoryService;
    }

    @Override
    public void configureConnectorDeploy(Connector connector) throws Exception {
        if (connector instanceof WebServiceDispatcher webServiceDispatcher) {
            configureSocketFactory(webServiceDispatcher);
        } else if (connector instanceof WebServiceReceiver webServiceReceiver) {
            configureSocketFactory(webServiceReceiver);
        }
    }

    @Override
    public void configureReceiver(WebServiceReceiver connector) throws Exception {
        if (listenerContainer == null) {
            super.configureReceiver(connector);
            return;
        }

        var tlsContext = listenerContainer.sslContext();

        var httpsServer = HttpsServer.create();
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(tlsContext) {
            @Override
            public void configure(HttpsParameters params) {
                var sslParams = tlsContext.getDefaultSSLParameters();

                sslParams.setProtocols(listenerContainer.protocols());
                sslParams.setCipherSuites(listenerContainer.ciphers());

                if (ClientAuthMode.REQUESTED == listenerContainer.clientAuthMode()) {
                    sslParams.setWantClientAuth(true);
                } else if (ClientAuthMode.REQUIRED == listenerContainer.clientAuthMode()) {
                    sslParams.setNeedClientAuth(true);
                }

                params.setSSLParameters(sslParams);
            }
        });

        connector.setServer(httpsServer);
    }

    @Override
    public void configureDispatcher(WebServiceDispatcher connector, WebServiceDispatcherProperties connectorProperties, Map<String, Object> requestContext) throws Exception {
        SSLSocketFactory socketFactory = new SSLSocketFactoryWrapper(
            senderContainer.sslContext().getSocketFactory(),
            senderContainer.protocols(),
            senderContainer.ciphers()
        );

        // Wat?
        requestContext.put("com.sun.xml.internal.ws.transport.https.client.SSLSocketFactory", socketFactory);
        requestContext.put("com.sun.xml.ws.transport.https.client.SSLSocketFactory", socketFactory); // JAX-WS RI
    }

    private void configureSocketFactory(WebServiceReceiver connector) {
        var tlsConnectorProperties = connector.getConnectorProperties().getPluginProperties()
            .stream()
            .filter(TLSConnectorProperties.class::isInstance)
            .findFirst()
            .map(TLSConnectorProperties.class::cast)
            .orElse(null);

        if (tlsConnectorProperties != null && tlsConnectorProperties.isTlsManagerEnabled()) {
            listenerContainer = socketFactoryService.generateTLSContext(tlsConnectorProperties);
        } else {
            try {
                super.configureConnectorDeploy(connector);
            } catch (Exception e) {
                log.error("Error creating non-TLS socket factory", e);
                throw new RuntimeException(e);
            }
        }
    }

    private void configureSocketFactory(WebServiceDispatcher connector) {
        var tlsConnectorProperties = connector.getConnectorProperties().getPluginProperties()
            .stream()
            .filter(TLSConnectorProperties.class::isInstance)
            .findFirst()
            .map(TLSConnectorProperties.class::cast)
            .orElse(null);

        if (tlsConnectorProperties != null && tlsConnectorProperties.isTlsManagerEnabled()) {
            senderContainer = socketFactoryService.generateTLSContextSender(tlsConnectorProperties);

            var socketConnectionFactory = socketFactoryService.getConnectorSocketFactory(senderContainer);
            if (socketConnectionFactory != null) {
                connector.getSocketFactoryRegistry().register("https", socketConnectionFactory);
            }
        } else {
            try {
                super.configureConnectorDeploy(connector);
            } catch (Exception e) {
                log.error("Error creating non-TLS socket factory", e);
                throw new RuntimeException(e);
            }
        }
    }
}
