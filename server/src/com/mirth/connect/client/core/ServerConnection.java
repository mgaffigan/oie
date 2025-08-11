/*
 * Copyright (c) Mirth Corporation. All rights reserved.
 * 
 * http://www.mirthcorp.com
 * 
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.client.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultConnectionKeepAliveStrategy;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.ssl.SSLContexts;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.glassfish.jersey.client.ClientRequest;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.client.spi.AsyncConnectorCallback;
import org.glassfish.jersey.client.spi.Connector;
import org.glassfish.jersey.message.internal.OutboundMessageContext;
import org.glassfish.jersey.message.internal.Statuses;

import com.mirth.connect.client.core.Operation.ExecuteType;
import com.mirth.connect.donkey.util.xstream.SerializerException;
import com.mirth.connect.model.converters.ObjectXMLSerializer;
import com.mirth.connect.util.HttpUtil;
import com.mirth.connect.util.MirthSSLUtil;

public class ServerConnection implements Connector {

    public static final String EXECUTE_TYPE_PROPERTY = "executeType";
    public static final String OPERATION_PROPERTY = "operation";
    public static final String CUSTOM_HEADERS_PROPERTY = "customHeaders";

    private static final int CONNECT_TIMEOUT = 10000;
    private static final int IDLE_TIMEOUT = 300000;

    private Logger logger = LogManager.getLogger(getClass());
    private Registry<ConnectionSocketFactory> socketFactoryRegistry;
    private PoolingHttpClientConnectionManager httpClientConnectionManager;
    private CookieStore cookieStore;
    private SocketConfig socketConfig;
    private RequestConfig requestConfig;
    private CloseableHttpClient client;
    private SSLConnectionSocketFactory sslConnectionSocketFactory;
    private final Operation currentOp = new Operation(null, null, null, false);
    private HttpRequestBase syncRequestBase;
    private HttpRequestBase abortPendingRequestBase;
    private HttpClientContext abortPendingClientContext = null;
    private final AbortTask abortTask = new AbortTask();
    private ExecutorService abortExecutor = Executors.newSingleThreadExecutor();
    private IdleConnectionMonitor idleConnectionMonitor;
    private ConnectionKeepAliveStrategy keepAliveStrategy;
    private SSLContext sslContext;

    public ServerConnection(int timeout, String[] httpsProtocols, String[] httpsCipherSuites) {
        this(timeout, httpsProtocols, httpsCipherSuites, false, null);
    }

    public ServerConnection(int timeout, String[] httpsProtocols, String[] httpsCipherSuites, boolean allowHTTP) {
        this(timeout, httpsProtocols, httpsCipherSuites, allowHTTP, null);
    }

    public ServerConnection(int timeout, String[] httpsProtocols, String[] httpsCipherSuites, boolean allowHTTP, String baseUrl) {
        sslContext = null;
        try {
            sslContext = SSLContexts.custom().loadTrustMaterial(null, new TrustSelfSignedStrategy()).build();
        } catch (Exception e) {
            logger.error("Unable to build SSL context.", e);
        }

        String[] enabledProtocols = MirthSSLUtil.getEnabledHttpsProtocols(httpsProtocols);
        String[] enabledCipherSuites = MirthSSLUtil.getEnabledHttpsCipherSuites(httpsCipherSuites);
        sslConnectionSocketFactory = new SSLConnectionSocketFactory(sslContext, enabledProtocols, enabledCipherSuites, NoopHostnameVerifier.INSTANCE);
        RegistryBuilder<ConnectionSocketFactory> builder = RegistryBuilder.<ConnectionSocketFactory> create().register("https", sslConnectionSocketFactory);
        if (allowHTTP) {
            builder.register("http", PlainConnectionSocketFactory.getSocketFactory());
        }
        socketFactoryRegistry = builder.build();

        cookieStore = new BasicCookieStore();
        socketConfig = SocketConfig.custom().setSoTimeout(timeout).build();
        requestConfig = RequestConfig.custom().setConnectTimeout(CONNECT_TIMEOUT).setSocketTimeout(timeout).build();
        keepAliveStrategy = new CustomKeepAliveStrategy();

        createClient();

    java.net.URL.setURLStreamHandlerFactory(new ServerURLStreamHandlerFactory(baseUrl));
    }

    /** Wrapper that enforces enabled protocols/ciphers like our Apache client does. */
    private static class TlsConfiguringSSLSocketFactory extends SSLSocketFactory {
        private final SSLSocketFactory delegate;
        private final String[] protos;
        private final String[] ciphers;

        TlsConfiguringSSLSocketFactory(SSLSocketFactory delegate) {
            this.delegate = delegate;
            this.protos = MirthSSLUtil.getEnabledHttpsProtocols(null);
            this.ciphers = MirthSSLUtil.getEnabledHttpsCipherSuites(null);
        }

        private SSLSocket tune(SSLSocket s) {
            try {
                if (protos != null && protos.length > 0) {
                    String[] supported = s.getSupportedProtocols();
                    java.util.List<String> supportedList = java.util.Arrays.asList(supported);
                    java.util.List<String> enabled = new java.util.ArrayList<String>();
                    for (String p : protos) if (supportedList.contains(p)) enabled.add(p);
                    if (!enabled.isEmpty()) s.setEnabledProtocols(enabled.toArray(new String[0]));
                }
                if (ciphers != null && ciphers.length > 0) {
                    String[] supported = s.getSupportedCipherSuites();
                    java.util.List<String> supportedList = java.util.Arrays.asList(supported);
                    java.util.List<String> enabled = new java.util.ArrayList<String>();
                    for (String c : ciphers) if (supportedList.contains(c)) enabled.add(c);
                    if (!enabled.isEmpty()) s.setEnabledCipherSuites(enabled.toArray(new String[0]));
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

    /**
     * A URLStreamHandlerFactory that handles the custom scheme oieserver:/// by resolving against
     * a base server URL and applying the same SSL and cookie configuration as this ServerConnection.
     */
    public class ServerURLStreamHandlerFactory implements java.net.URLStreamHandlerFactory {
        private final String baseUrl;

        public ServerURLStreamHandlerFactory(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        @Override
        public java.net.URLStreamHandler createURLStreamHandler(String protocol) {
            if ("oieserver".equalsIgnoreCase(protocol)) {
                return new Handler();
            }
            return null;
        }

        class Handler extends java.net.URLStreamHandler {
            @Override
            protected java.net.URLConnection openConnection(java.net.URL u) throws IOException {
                try {
                    String path = u.getPath() != null ? u.getPath() : "/";
                    String query = u.getQuery();
                    String ref = u.getRef();
                    StringBuilder file = new StringBuilder(path);
                    if (query != null && !query.isEmpty()) file.append('?').append(query);
                    if (ref != null && !ref.isEmpty()) file.append('#').append(ref);

                    if (baseUrl == null || baseUrl.isEmpty()) {
                        throw new IOException("Base server URL is not set; cannot resolve oieserver URL");
                    }
                    java.net.URL base;
                    try {
                        base = new java.net.URL(baseUrl);
                    } catch (java.net.MalformedURLException e) {
                        throw new IOException("Invalid base server URL: " + baseUrl, e);
                    }
                    java.net.URL target = new java.net.URL(base, file.toString());
                    java.net.URLConnection conn = target.openConnection();
                    if (conn instanceof HttpsURLConnection) {
                        HttpsURLConnection https = (HttpsURLConnection) conn;
                        https.setSSLSocketFactory(new TlsConfiguringSSLSocketFactory(sslContext.getSocketFactory()));
                        https.setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
                    }
                    // Copy cookies from our CookieStore into the request header so the session is maintained
                    if (cookieStore != null && !cookieStore.getCookies().isEmpty()) {
                        StringBuilder cookieHeader = new StringBuilder();
                        for (org.apache.http.cookie.Cookie c : cookieStore.getCookies()) {
                            if (cookieHeader.length() > 0) cookieHeader.append("; ");
                            cookieHeader.append(c.getName()).append("=").append(c.getValue());
                        }
                        conn.setRequestProperty("Cookie", cookieHeader.toString());
                    }
                    conn.setRequestProperty("X-Requested-With", BrandingConstants.CLIENT_CONNECTION_HEADER);
                    return conn;
                } catch (Exception e) {
                    if (e instanceof IOException) throw (IOException) e;
                    throw new IOException("Failed to open oieserver connection", e);
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ClientResponse apply(ClientRequest request) {
        Operation operation = (Operation) request.getConfiguration().getProperty(OPERATION_PROPERTY);
        if (operation == null) {
            throw new ProcessingException("No operation provided for request: " + request);
        }

        ExecuteType executeType = (ExecuteType) request.getConfiguration().getProperty(EXECUTE_TYPE_PROPERTY);
        if (executeType == null) {
            executeType = operation.getExecuteType();
        }

        Map<String, List<String>> customHeaders = (Map<String, List<String>>) request.getConfiguration().getProperty(CUSTOM_HEADERS_PROPERTY);

        if (logger.isDebugEnabled()) {
            StringBuilder debugMessage = new StringBuilder(operation.getDisplayName()).append('\n');
            debugMessage.append(request.getMethod()).append(' ').append(request.getUri());
            logger.debug(debugMessage.toString());
        }

        try {
            switch (executeType) {
                case SYNC:
                    return executeSync(request, operation, customHeaders);
                case ASYNC:
                    return executeAsync(request, customHeaders);
                case ABORT_PENDING:
                    return executeAbortPending(request, customHeaders);
            }
        } catch (ClientException e) {
            throw new ProcessingException(e);
        }

        return null;
    }

    @Override
    public Future<?> apply(ClientRequest request, AsyncConnectorCallback callback) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
        return "Mirth Server Connection";
    }

    @Override
    public void close() {
        // Do nothing
    }

    private void createClient() {
        httpClientConnectionManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        httpClientConnectionManager.setDefaultMaxPerRoute(5);
        httpClientConnectionManager.setDefaultSocketConfig(socketConfig);
        // MIRTH-3962: The stale connection settings has been deprecated, and this is recommended instead
        httpClientConnectionManager.setValidateAfterInactivity(5000);

        HttpClientBuilder clientBuilder = HttpClients.custom().setConnectionManager(httpClientConnectionManager).setDefaultCookieStore(cookieStore).setKeepAliveStrategy(keepAliveStrategy);
        HttpUtil.configureClientBuilder(clientBuilder);

        client = clientBuilder.build();

        idleConnectionMonitor = new IdleConnectionMonitor();
        idleConnectionMonitor.start();
    }

    public synchronized void shutdown() {
        idleConnectionMonitor.shutdown();

        // Shutdown the abort thread
        abortExecutor.shutdownNow();

        HttpClientUtils.closeQuietly(client);
    }

    public synchronized void restart() {
        shutdown();
        abortExecutor = Executors.newSingleThreadExecutor();
        createClient();
    }

    /**
     * Aborts the request if the currentOp is equal to the passed operation, or if the passed
     * operation is null
     * 
     * @param operation
     */
    public void abort(Collection<Operation> operations) {
        synchronized (currentOp) {
            if (operations.contains(currentOp)) {
                syncRequestBase.abort();
            }
        }
    }

    /**
     * Allows one request at a time.
     */
    private synchronized ClientResponse executeSync(ClientRequest request, Operation operation, Map<String, List<String>> customHeaders) throws ClientException {
        synchronized (currentOp) {
            currentOp.setName(operation.getName());
            currentOp.setDisplayName(operation.getDisplayName());
            currentOp.setAuditable(operation.isAuditable());
        }

        HttpRequestBase requestBase = null;
        CloseableHttpResponse response = null;
        boolean shouldClose = true;

        try {
            requestBase = setupRequestBase(request, customHeaders, ExecuteType.SYNC);
            response = client.execute(requestBase);
            ClientResponse responseContext = handleResponse(request, requestBase, response, true);
            if (responseContext.hasEntity()) {
                shouldClose = false;
            }
            return responseContext;
        } catch (Error e) {
            // If an error occurred we can't guarantee the state of the client, so close it
            HttpUtil.closeVeryQuietly(response);
            restart();
            throw e;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                HttpUtil.closeVeryQuietly(response);
                restart();
            }

            if (requestBase != null && requestBase.isAborted()) {
                throw new RequestAbortedException(e);
            } else if (e instanceof ClientException) {
                throw (ClientException) e;
            }
            throw new ClientException(e);
        } finally {
            if (shouldClose) {
                HttpUtil.closeVeryQuietly(response);

                synchronized (currentOp) {
                    currentOp.setName(null);
                    currentOp.setDisplayName(null);
                    currentOp.setAuditable(false);
                }
            }
        }
    }

    /**
     * Allows multiple simultaneous requests.
     */
    private ClientResponse executeAsync(ClientRequest request, Map<String, List<String>> customHeaders) throws ClientException {
        HttpRequestBase requestBase = null;
        CloseableHttpResponse response = null;
        boolean shouldClose = true;

        try {
            requestBase = setupRequestBase(request, customHeaders, ExecuteType.ASYNC);
            response = client.execute(requestBase);
            ClientResponse responseContext = handleResponse(request, requestBase, response);
            if (responseContext.hasEntity()) {
                shouldClose = false;
            }
            return responseContext;
        } catch (Error e) {
            // If an error occurred we can't guarantee the state of the client, so close it
            HttpUtil.closeVeryQuietly(response);
            restart();
            throw e;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                HttpUtil.closeVeryQuietly(response);
                restart();
            }

            if (requestBase != null && requestBase.isAborted()) {
                throw new RequestAbortedException(e);
            } else if (e instanceof ClientException) {
                throw (ClientException) e;
            }
            throw new ClientException(e);
        } finally {
            if (shouldClose) {
                HttpUtil.closeVeryQuietly(response);
            }
        }
    }

    /**
     * The requests sent through this channel will be aborted on the client side when a new request
     * arrives. Currently there is no guarantee of the order that pending requests will be sent.
     */
    private ClientResponse executeAbortPending(ClientRequest request, Map<String, List<String>> customHeaders) throws ClientException {
        // TODO: Make order sequential
        abortTask.incrementRequestsInQueue();

        synchronized (abortExecutor) {
            if (!abortExecutor.isShutdown() && !abortTask.isRunning()) {
                abortExecutor.execute(abortTask);
            }

            HttpRequestBase requestBase = null;
            CloseableHttpResponse response = null;
            boolean shouldClose = true;

            try {
                abortPendingClientContext = HttpClientContext.create();
                abortPendingClientContext.setRequestConfig(requestConfig);

                requestBase = setupRequestBase(request, customHeaders, ExecuteType.ABORT_PENDING);

                abortTask.setAbortAllowed(true);
                response = client.execute(requestBase, abortPendingClientContext);
                abortTask.setAbortAllowed(false);

                ClientResponse responseContext = handleResponse(request, requestBase, response);
                if (responseContext.hasEntity()) {
                    shouldClose = false;
                }
                return responseContext;
            } catch (Error e) {
                // If an error occurred we can't guarantee the state of the client, so close it
                HttpUtil.closeVeryQuietly(response);
                restart();
                throw e;
            } catch (Exception e) {
                if (e instanceof IllegalStateException) {
                    HttpUtil.closeVeryQuietly(response);
                    restart();
                }

                if (requestBase != null && requestBase.isAborted()) {
                    return new ClientResponse(Status.NO_CONTENT, request);
                } else if (e instanceof ClientException) {
                    throw (ClientException) e;
                }
                throw new ClientException(e);
            } finally {
                abortTask.decrementRequestsInQueue();
                if (shouldClose) {
                    HttpUtil.closeVeryQuietly(response);
                }
            }
        }
    }

    private HttpRequestBase createRequestBase(String method) {
        HttpRequestBase requestBase = null;

        if (StringUtils.equalsIgnoreCase(HttpGet.METHOD_NAME, method)) {
            requestBase = new HttpGet();
        } else if (StringUtils.equalsIgnoreCase(HttpPost.METHOD_NAME, method)) {
            requestBase = new HttpPost();
        } else if (StringUtils.equalsIgnoreCase(HttpPut.METHOD_NAME, method)) {
            requestBase = new HttpPut();
        } else if (StringUtils.equalsIgnoreCase(HttpDelete.METHOD_NAME, method)) {
            requestBase = new HttpDelete();
        } else if (StringUtils.equalsIgnoreCase(HttpOptions.METHOD_NAME, method)) {
            requestBase = new HttpOptions();
        } else if (StringUtils.equalsIgnoreCase(HttpPatch.METHOD_NAME, method)) {
            requestBase = new HttpPatch();
        }

        requestBase.setConfig(requestConfig);
        return requestBase;
    }

    private HttpRequestBase getRequestBase(ExecuteType executeType, String method) {
        HttpRequestBase requestBase = createRequestBase(method);

        if (executeType == ExecuteType.SYNC) {
            syncRequestBase = requestBase;
        } else if (executeType == ExecuteType.ABORT_PENDING) {
            abortPendingRequestBase = requestBase;
        }

        return requestBase;
    }

    private HttpRequestBase setupRequestBase(ClientRequest request, Map<String, List<String>> customHeaders, ExecuteType executeType) {
        HttpRequestBase requestBase = getRequestBase(executeType, request.getMethod());
        requestBase.setURI(request.getUri());

        requestBase.addHeader("X-Requested-With", BrandingConstants.CLIENT_CONNECTION_HEADER);

        for (Entry<String, List<String>> entry : request.getStringHeaders().entrySet()) {
            for (String value : entry.getValue()) {
                requestBase.addHeader(entry.getKey(), value);
            }
        }

        if (MapUtils.isNotEmpty(customHeaders)) {
            
            for (Entry<String, List<String>> entry : customHeaders.entrySet()) {
                String key = entry.getKey();

                if (CollectionUtils.isNotEmpty(entry.getValue())) {
                    for (String value : entry.getValue()) {
                        requestBase.addHeader(key, value);
                    }
                }
            }
        }

        if (request.hasEntity() && requestBase instanceof HttpEntityEnclosingRequestBase) {
            final HttpEntityEnclosingRequestBase entityRequestBase = (HttpEntityEnclosingRequestBase) requestBase;
            entityRequestBase.setEntity(new ClientRequestEntity(request));
        }

        return requestBase;
    }

    private ClientResponse handleResponse(ClientRequest request, HttpRequestBase requestBase, CloseableHttpResponse response) throws IOException, ClientException {
        return handleResponse(request, requestBase, response, false);
    }

    private ClientResponse handleResponse(ClientRequest request, HttpRequestBase requestBase, CloseableHttpResponse response, boolean sync) throws IOException, ClientException {
        StatusLine statusLine = response.getStatusLine();
        int statusCode = statusLine.getStatusCode();

        ClientResponse responseContext = new MirthClientResponse(Statuses.from(statusCode), request);

        MultivaluedMap<String, String> headerMap = new MultivaluedHashMap<String, String>();
        for (Header header : response.getAllHeaders()) {
            headerMap.add(header.getName(), header.getValue());
        }
        responseContext.headers(headerMap);

        HttpEntity responseEntity = response.getEntity();
        if (responseEntity != null) {
            responseContext.setEntityStream(new EntityInputStreamWrapper(response, responseEntity.getContent(), sync));
        }

        if (statusCode == HttpStatus.SC_UNAUTHORIZED) {
            if (responseContext.hasEntity()) {
                try {
                    Object entity = responseContext.readEntity(Object.class);
                    throw new UnauthorizedException(statusLine.toString(), entity);
                } catch (ProcessingException e) {
                }
            }
            throw new UnauthorizedException(statusLine.toString());
        } else if (statusCode == HttpStatus.SC_FORBIDDEN) {
            throw new ForbiddenException(statusLine.toString());
        }

        if (statusCode >= 400) {
            if (responseContext.hasEntity()) {
                try {
                    ContentType contentType = null;
                    String contentTypeString = responseContext.getHeaderString("Content-Type");
                    if (StringUtils.isNotBlank(contentTypeString)) {
                        contentType = ContentType.parse(contentTypeString);
                    }

                    String charset = null;
                    if (contentType != null) {
                        Charset charsetObj = contentType.getCharset();
                        if (charsetObj != null) {
                            charset = charsetObj.name();
                        }
                    }
                    if (charset == null) {
                        charset = "UTF-8";
                    }

                    Throwable t = null;
                    String entityString = IOUtils.toString(responseContext.getEntityStream(), charset);

                    if (contentType == null || StringUtils.equalsIgnoreCase(contentType.getMimeType(), MediaType.APPLICATION_XML)) {
                        try {
                            Object entity = ObjectXMLSerializer.getInstance().deserialize(entityString, Object.class);
                            if (entity instanceof Throwable) {
                                t = (Throwable) entity;
                            } else {
                                t = new EntityException(entity);
                            }
                        } catch (SerializerException e) {
                            try {
                                t = ObjectXMLSerializer.getInstance().deserialize(entityString, Throwable.class);
                            } catch (SerializerException e2) {
                            }
                        }
                    }

                    if (t == null) {
                        t = new EntityException(entityString);
                    }

                    throw new ClientException("Method failed: " + statusLine, t);
                } catch (ProcessingException e) {
                }
            }
            throw new ClientException("Method failed: " + statusLine);
        }

        return responseContext;
    }

    private class ClientRequestEntity extends AbstractHttpEntity {

        private ClientRequest request;

        public ClientRequestEntity(ClientRequest request) {
            this.request = request;
        }

        @Override
        public boolean isRepeatable() {
            return false;
        }

        @Override
        public long getContentLength() {
            return -1;
        }

        @Override
        public InputStream getContent() throws UnsupportedOperationException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void writeTo(final OutputStream outstream) throws IOException {
            request.setStreamProvider(new OutboundMessageContext.StreamProvider() {
                @Override
                public OutputStream getOutputStream(int contentLength) throws IOException {
                    return outstream;
                }
            });
            request.writeEntity();
        }

        @Override
        public boolean isStreaming() {
            return true;
        }
    }

    private class EntityInputStreamWrapper extends InputStream {

        private CloseableHttpResponse response;
        private InputStream delegate;
        private boolean sync;

        public EntityInputStreamWrapper(CloseableHttpResponse response, InputStream delegate, boolean sync) {
            this.response = response;
            this.delegate = delegate;
            this.sync = sync;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] b) throws IOException {
            return delegate.read(b);
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return delegate.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return delegate.skip(n);
        }

        @Override
        public int available() throws IOException {
            return delegate.available();
        }

        @Override
        public void close() throws IOException {
            try {
                delegate.close();
            } finally {
                HttpClientUtils.closeQuietly(response);

                if (sync) {
                    synchronized (currentOp) {
                        currentOp.setName(null);
                        currentOp.setDisplayName(null);
                        currentOp.setAuditable(false);
                    }
                }
            }
        }

        @Override
        public synchronized void mark(int readlimit) {
            delegate.mark(readlimit);
        }

        @Override
        public synchronized void reset() throws IOException {
            delegate.reset();
        }

        @Override
        public boolean markSupported() {
            return delegate.markSupported();
        }
    }

    private class AbortTask implements Runnable {
        private final AtomicBoolean running = new AtomicBoolean(false);
        private int requestsInQueue = 0;
        private boolean abortAllowed = false;

        public synchronized void incrementRequestsInQueue() {
            requestsInQueue++;
        }

        public synchronized void decrementRequestsInQueue() {
            requestsInQueue--;
        }

        public synchronized void setAbortAllowed(boolean abortAllowed) {
            this.abortAllowed = abortAllowed;
        }

        public boolean isRunning() {
            return running.get();
        }

        @Override
        public void run() {
            try {
                running.set(true);
                while (true) {
                    synchronized (this) {
                        if (requestsInQueue == 0) {
                            return;
                        }
                        if (requestsInQueue > 1 && abortAllowed && abortPendingClientContext.isRequestSent()) {
                            abortPendingRequestBase.abort();
                            abortAllowed = false;
                        }
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            } finally {
                running.set(false);
            }
        }
    }

    private class IdleConnectionMonitor extends Thread {

        private volatile boolean shutdown;

        @Override
        public void run() {
            try {
                while (!shutdown) {
                    synchronized (this) {
                        wait(5000);
                        httpClientConnectionManager.closeExpiredConnections();
                        httpClientConnectionManager.closeIdleConnections(IDLE_TIMEOUT, TimeUnit.MILLISECONDS);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        public void shutdown() {
            shutdown = true;
            synchronized (this) {
                notifyAll();
            }
            try {
                join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private class CustomKeepAliveStrategy extends DefaultConnectionKeepAliveStrategy {

        @Override
        public long getKeepAliveDuration(HttpResponse response, HttpContext context) {
            long keepAlive = super.getKeepAliveDuration(response, context);

            if (keepAlive <= 0) {
                keepAlive = IDLE_TIMEOUT;
            }

            return keepAlive;
        }
    }
}