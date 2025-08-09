package com.mirth.connect.plugins.webdemo.net;

import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.util.MirthSSLUtil;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.*;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * URLStreamHandlerFactory for the custom scheme oieserver:/// which maps to
 * https://localhost:8443/ and configures JSSE per MirthSSLUtil/PlatformUI.
 * For POC, a trust-all manager is used, scoped to these connections only.
 */
public class OieServerURLStreamHandlerFactory implements URLStreamHandlerFactory {

    private static final String PROTOCOL = "oieserver";

    @Override
    public URLStreamHandler createURLStreamHandler(String protocol) {
        if (PROTOCOL.equalsIgnoreCase(protocol)) {
            return new Handler();
        }
        return null;
    }

    static class Handler extends URLStreamHandler {
        @Override
        protected URLConnection openConnection(URL u) throws IOException {
            try {
                String path = u.getPath() != null ? u.getPath() : "/";
                String query = u.getQuery();
                String ref = u.getRef();
                StringBuilder file = new StringBuilder(path);
                if (query != null && !query.isEmpty()) {
                    file.append('?').append(query);
                }
                if (ref != null && !ref.isEmpty()) {
                    file.append('#').append(ref);
                }
                URL httpsUrl = new URL("https", "localhost", 8443, file.toString());
                URLConnection conn = httpsUrl.openConnection();
                if (conn instanceof HttpsURLConnection) {
                    HttpsURLConnection https = (HttpsURLConnection) conn;
                    https.setSSLSocketFactory(buildSocketFactory());
                    https.setHostnameVerifier(new HostnameVerifier() {
                        public boolean verify(String hostname, SSLSession session) { return true; }
                    });
                }
                return conn;
            } catch (GeneralSecurityException e) {
                throw new IOException("Failed to initialize SSL for oieserver:// handler", e);
            }
        }

        private SSLSocketFactory buildSocketFactory() throws NoSuchAlgorithmException, KeyManagementException {
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }};

            SSLContext sc = SSLContext.getInstance("TLS"); // allow TLSv1.3/v1.2
            sc.init(null, trustAllCerts, new SecureRandom());
            return new TlsConfiguringSSLSocketFactory(sc.getSocketFactory());
        }

        // Wrapper that enforces enabled protocols/ciphers based on PlatformUI/MirthSSLUtil
        static class TlsConfiguringSSLSocketFactory extends SSLSocketFactory {
            private final SSLSocketFactory delegate;
            private final String[] protos = MirthSSLUtil.getEnabledHttpsProtocols(PlatformUI.HTTPS_PROTOCOLS);
            private final String[] ciphers = MirthSSLUtil.getEnabledHttpsCipherSuites(PlatformUI.HTTPS_CIPHER_SUITES);

            TlsConfiguringSSLSocketFactory(SSLSocketFactory delegate) { this.delegate = delegate; }

            private SSLSocket tune(SSLSocket s) {
                try {
                    if (protos != null && protos.length > 0) {
                        String[] supported = s.getSupportedProtocols();
                        String[] enabled = Arrays.stream(protos).filter(p -> Arrays.asList(supported).contains(p)).toArray(String[]::new);
                        if (enabled.length > 0) s.setEnabledProtocols(enabled);
                    }
                    if (ciphers != null && ciphers.length > 0) {
                        String[] supported = s.getSupportedCipherSuites();
                        String[] enabled = Arrays.stream(ciphers).filter(c -> Arrays.asList(supported).contains(c)).toArray(String[]::new);
                        if (enabled.length > 0) s.setEnabledCipherSuites(enabled);
                    }
                } catch (Exception ignore) {}
                return s;
            }

            @Override public String[] getDefaultCipherSuites() { return delegate.getDefaultCipherSuites(); }
            @Override public String[] getSupportedCipherSuites() { return delegate.getSupportedCipherSuites(); }
            @Override public java.net.Socket createSocket(java.net.Socket s, String host, int port, boolean autoClose) throws java.io.IOException { java.net.Socket sock = delegate.createSocket(s, host, port, autoClose); return sock instanceof SSLSocket ? tune((SSLSocket) sock) : sock; }
            @Override public java.net.Socket createSocket(String host, int port) throws java.io.IOException { java.net.Socket sock = delegate.createSocket(host, port); return sock instanceof SSLSocket ? tune((SSLSocket) sock) : sock; }
            @Override public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException { java.net.Socket sock = delegate.createSocket(host, port, localHost, localPort); return sock instanceof SSLSocket ? tune((SSLSocket) sock) : sock; }
            @Override public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException { java.net.Socket sock = delegate.createSocket(host, port); return sock instanceof SSLSocket ? tune((SSLSocket) sock) : sock; }
            @Override public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException { java.net.Socket sock = delegate.createSocket(address, port, localAddress, localPort); return sock instanceof SSLSocket ? tune((SSLSocket) sock) : sock; }
        }
    }
}
