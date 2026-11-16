// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.connectors.http;

import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.servlet.ServletRequest;

import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.openintegrationengine.engine.tls.ClientAuth;
import org.openintegrationengine.engine.tls.TlsContext;
import org.openintegrationengine.engine.tls.TlsContextProvider;
import org.openintegrationengine.engine.tls.TlsContextRequest;

import com.mirth.connect.donkey.server.channel.Connector;
import com.mirth.connect.server.controllers.ControllerFactory;

public class DefaultHttpConfiguration implements HttpConfiguration {

    @Override
    public void configureConnectorDeploy(Connector connector) throws Exception {
        if (connector instanceof HttpDispatcher) {
            /*
             * Resolved unconditionally rather than behind isTlsEnabled, because the destination URL
             * decides per message whether this registry entry is used.
             */
            TlsContext tlsContext = getTlsContextProvider().createTlsContext(TlsContextRequest.forConnector(connector));
            configureSocketFactoryRegistry(tlsContext, ((HttpDispatcher) connector).getSocketFactoryRegistry());
        }
    }

    @Override
    public void configureConnectorUndeploy(Connector connector) {}

    @Override
    public void configureReceiver(HttpReceiver connector) throws Exception {
        TlsContextProvider tlsContextProvider = getTlsContextProvider();
        TlsContextRequest request = TlsContextRequest.forConnector(connector);

        ServerConnector listener;

        if (tlsContextProvider.isTlsEnabled(request)) {
            listener = createSslListener(connector, tlsContextProvider.createTlsContext(request));
        } else {
            listener = new ServerConnector(connector.getServer(), new HttpConnectionFactory(createHttpConfig(connector)));
        }

        listener.setHost(connector.getHost());
        listener.setPort(connector.getPort());
        listener.setIdleTimeout(connector.getTimeout());
        connector.getServer().addConnector(listener);
    }

    /**
     * Builds the Jetty configuration the listener is created with. An implementation that overrides
     * {@link #configureReceiver(HttpReceiver)} should build its own listener from this rather than
     * repeating the settings, so that it keeps up with changes to the defaults.
     */
    protected org.eclipse.jetty.server.HttpConfiguration createHttpConfig(HttpReceiver connector) {
        org.eclipse.jetty.server.HttpConfiguration httpConfig = new org.eclipse.jetty.server.HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        httpConfig.setSendXPoweredBy(false);
        httpConfig.setRequestHeaderSize(connector.getRequestHeaderSize());
        return httpConfig;
    }

    private ServerConnector createSslListener(HttpReceiver connector, TlsContext tlsContext) {
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setSslContext(tlsContext.getSslContext());

        /*
         * Jetty's own exclusions are cleared first so that the enabled lists are exactly what was
         * resolved. They have already been intersected with what the JVM supports.
         */
        sslContextFactory.setExcludeProtocols();
        sslContextFactory.setExcludeCipherSuites();
        sslContextFactory.setIncludeProtocols(tlsContext.getEnabledProtocols());
        sslContextFactory.setIncludeCipherSuites(tlsContext.getEnabledCipherSuites());

        sslContextFactory.setWantClientAuth(tlsContext.getClientAuth() == ClientAuth.WANT);
        sslContextFactory.setNeedClientAuth(tlsContext.getClientAuth() == ClientAuth.NEED);

        // Host name checking applies to a peer we connect to, not to one connecting to us.
        sslContextFactory.setEndpointIdentificationAlgorithm(null);

        org.eclipse.jetty.server.HttpConfiguration httpConfig = createHttpConfig(connector);
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(connector.getPort());
        httpConfig.addCustomizer(new SecureRequestCustomizer());

        return new ServerConnector(connector.getServer(), new SslConnectionFactory(sslContextFactory, HttpVersion.HTTP_1_1.asString()), new HttpConnectionFactory(httpConfig));
    }

    @Override
    public void configureDispatcher(HttpDispatcher connector, HttpDispatcherProperties connectorProperties) throws Exception {}

    @Override
    public void configureSocketFactoryRegistry(TlsContext tlsContext, RegistryBuilder<ConnectionSocketFactory> registry) throws Exception {
        registry.register("https", new SSLConnectionSocketFactory(tlsContext.getSslContext(), tlsContext.getEnabledProtocols(), tlsContext.getEnabledCipherSuites(), getHostnameVerifier(tlsContext)));
    }

    /**
     * Apache HttpClient takes a verifier rather than reading
     * {@link javax.net.ssl.SSLParameters#setEndpointIdentificationAlgorithm(String)}, so the
     * policy is translated here. An explicit verifier wins, which is how a TLS extension expresses
     * a rule the standard algorithm cannot.
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

    @Override
    public Map<String, Object> getRequestInformation(ServletRequest request) {
        return new HashMap<String, Object>();
    }
}
