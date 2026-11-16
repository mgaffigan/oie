// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.ws;

import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocketFactory;

import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.openintegrationengine.engine.tls.TlsContext;
import org.openintegrationengine.engine.tls.TlsContextProvider;
import org.openintegrationengine.engine.tls.TlsContextRequest;
import org.openintegrationengine.engine.tls.TlsRole;

import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

public class DefaultWebServiceConfiguration implements WebServiceConfiguration {

    /**
     * Held from deploy so that {@link #configureDispatcher} can reuse it. It is resolved
     * unconditionally for a dispatcher, because the endpoint URL decides per message whether TLS is
     * used, so it is never null on the sending path.
     */
    private TlsContext senderTlsContext;

    @Override
    public void configureConnectorDeploy(Connector connector) throws Exception {
        if (connector instanceof WebServiceDispatcher) {
            senderTlsContext = getTlsContextProvider().createTlsContext(TlsContextRequest.forConnector(connector));
            ((WebServiceDispatcher) connector).getSocketFactoryRegistry().register("https", new SSLConnectionSocketFactory(senderTlsContext.getSslContext(), senderTlsContext.getEnabledProtocols(), senderTlsContext.getEnabledCipherSuites(), getHostnameVerifier(senderTlsContext)));
        }
    }

    @Override
    public void configureConnectorUndeploy(Connector connector) {}

    @Override
    public void configureReceiver(WebServiceReceiver connector) throws Exception {
        TlsContextProvider tlsContextProvider = getTlsContextProvider();
        TlsContextRequest request = TlsContextRequest.forConnector(connector);

        if (!tlsContextProvider.isTlsEnabled(request)) {
            connector.setServer(HttpServer.create());
            return;
        }

        final TlsContext tlsContext = tlsContextProvider.createTlsContext(request);

        HttpsServer httpsServer = HttpsServer.create();
        httpsServer.setHttpsConfigurator(new HttpsConfigurator(tlsContext.getSslContext()) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParameters = tlsContext.getSslContext().getDefaultSSLParameters();
                tlsContext.applyTo(sslParameters, TlsRole.SERVER);
                params.setSSLParameters(sslParameters);
            }
        });

        connector.setServer(httpsServer);
    }

    @Override
    public void configureDispatcher(WebServiceDispatcher connector, WebServiceDispatcherProperties connectorProperties, Map<String, Object> requestContext) throws Exception {
        SSLSocketFactory socketFactory = new SSLSocketFactoryWrapper(senderTlsContext.getSslContext().getSocketFactory(), senderTlsContext.getEnabledProtocols(), senderTlsContext.getEnabledCipherSuites());

        /*
         * The JAX-WS reference implementation takes its socket factory from these context keys and
         * exposes no standard alternative, so the internal names are unavoidable here. They are
         * kept in the engine rather than in an extension so that only one place has to track them.
         */
        requestContext.put("com.sun.xml.internal.ws.transport.https.client.SSLSocketFactory", socketFactory);
        requestContext.put("com.sun.xml.ws.transport.https.client.SSLSocketFactory", socketFactory); // JAX-WS RI
    }

    /**
     * Apache HttpClient takes a verifier rather than reading
     * {@link SSLParameters#setEndpointIdentificationAlgorithm(String)}, so the policy is translated
     * here. An explicit verifier wins, which is how a TLS extension expresses a rule the standard
     * algorithm cannot.
     */
    private static HostnameVerifier getHostnameVerifier(TlsContext tlsContext) {
        HostnameVerifier hostnameVerifier = tlsContext.getHostnameVerifier();

        if (hostnameVerifier != null) {
            return hostnameVerifier;
        }

        return tlsContext.isEndpointIdentificationEnabled() ? new DefaultHostnameVerifier() : NoopHostnameVerifier.INSTANCE;
    }

    private static TlsContextProvider getTlsContextProvider() {
        return ControllerFactory.getFactory().createExtensionController().getTlsContextProvider();
    }
}
