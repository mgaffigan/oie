// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

import org.apache.http.ssl.SSLContexts;

import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.util.MirthSSLUtil;

/**
 * The {@link TlsContextProvider} the engine uses when no extension supplies one.
 * <p>
 * This is deliberately minimal. It is the floor the extension point stands on, not a certificate
 * manager: it offers the JVM's default trust material and the protocol and cipher suite lists from
 * {@code mirth.properties}, and nothing else. Anything richer -- selecting an identity to present,
 * pinning trust, revocation checking, verifying a subject DN -- is what an extension is for.
 * <p>
 * Its behaviour is exactly what these connectors did before the extension point existed, so
 * installing no extension changes nothing:
 * <ul>
 * <li>No listener terminates TLS, and the TCP sender opens a plain socket.</li>
 * <li>The HTTP and Web Service senders reach {@code https} URLs with the system default
 * {@link SSLContext}.</li>
 * <li>Host name verification is off. This is carried here as an explicit policy rather than as the
 * hardcoded {@code NoopHostnameVerifier} it used to be, so an extension can turn it on without the
 * engine's default changing underneath existing channels.</li>
 * </ul>
 */
public class DefaultTlsContextProvider implements TlsContextProvider {

    public static final String PLUGIN_POINT = "Default TLS Context Provider";

    private final ConfigurationController configurationController;

    public DefaultTlsContextProvider() {
        this(ControllerFactory.getFactory().createConfigurationController());
    }

    public DefaultTlsContextProvider(ConfigurationController configurationController) {
        this.configurationController = configurationController;
    }

    /**
     * Always false. The engine on its own never terminates TLS on a channel listener and never
     * promotes a TCP socket to TLS; both require an extension.
     */
    @Override
    public boolean isTlsEnabled(TlsContextRequest request) {
        return false;
    }

    @Override
    public TlsContext createTlsContext(TlsContextRequest request) throws TlsConfigurationException {
        SSLContext sslContext;

        try {
            sslContext = SSLContexts.createSystemDefault();
        } catch (Exception e) {
            throw new TlsConfigurationException("Could not create the system default SSL context.", e);
        }

        boolean server = request.getRole() == TlsRole.SERVER;
        String[] requestedProtocols = server ? configurationController.getHttpsServerProtocols()
                : configurationController.getHttpsClientProtocols();
        String[] requestedCipherSuites = configurationController.getHttpsCipherSuites();

        /*
         * The lists are normally configured, but fall back rather than failing the deploy over a
         * missing property: an empty enabled list would make every handshake fail.
         */
        if (requestedProtocols == null) {
            requestedProtocols = server ? MirthSSLUtil.DEFAULT_HTTPS_SERVER_PROTOCOLS : MirthSSLUtil.DEFAULT_HTTPS_CLIENT_PROTOCOLS;
        }

        if (requestedCipherSuites == null) {
            requestedCipherSuites = MirthSSLUtil.DEFAULT_HTTPS_CIPHER_SUITES;
        }

        return new DefaultTlsContext(sslContext, MirthSSLUtil.getEnabledHttpsProtocols(requestedProtocols),
                MirthSSLUtil.getEnabledHttpsCipherSuites(requestedCipherSuites));
    }

    @Override
    public String getPluginPointName() {
        return PLUGIN_POINT;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    private static class DefaultTlsContext implements TlsContext {

        private final SSLContext sslContext;
        private final String[] enabledProtocols;
        private final String[] enabledCipherSuites;

        DefaultTlsContext(SSLContext sslContext, String[] enabledProtocols, String[] enabledCipherSuites) {
            this.sslContext = sslContext;
            this.enabledProtocols = enabledProtocols;
            this.enabledCipherSuites = enabledCipherSuites;
        }

        @Override
        public SSLContext getSslContext() {
            return sslContext;
        }

        @Override
        public String[] getEnabledProtocols() {
            return enabledProtocols.clone();
        }

        @Override
        public String[] getEnabledCipherSuites() {
            return enabledCipherSuites.clone();
        }

        @Override
        public ClientAuth getClientAuth() {
            return ClientAuth.NONE;
        }

        @Override
        public boolean isEndpointIdentificationEnabled() {
            return false;
        }

        @Override
        public HostnameVerifier getHostnameVerifier() {
            return null;
        }
    }
}
