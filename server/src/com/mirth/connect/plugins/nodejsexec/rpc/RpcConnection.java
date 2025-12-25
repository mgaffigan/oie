package com.mirth.connect.plugins.nodejsexec.rpc;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mirth.connect.plugins.nodejsexec.ExecutorException;

/**
 * Thread-safe JSON-RPC connection over Unix domain socket.
 * Handles message framing, request/response correlation, and incoming requests.
 */
public class RpcConnection implements AutoCloseable {
    private static final Logger logger = LogManager.getLogger(RpcConnection.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    private final SocketChannel socketChannel;
    private final BufferedReader reader;
    private final AtomicInteger nextRequestId = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, ResponseHandler> pendingRequests = new ConcurrentHashMap<>();
    private final RpcRequestHandler requestHandler;
    private final Thread readerThread;
    private volatile boolean closed = false;

    /**
     * Creates a new RPC connection.
     * 
     * @param socketPath Path to the Unix domain socket
     * @param requestHandler Handler for incoming requests from the remote side
     * @throws IOException if connection fails
     */
    public RpcConnection(String socketPath, RpcRequestHandler requestHandler) throws IOException {
        this.requestHandler = requestHandler;
        
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(Path.of(socketPath));
        socketChannel = SocketChannel.open(StandardProtocolFamily.UNIX);
        socketChannel.connect(address);
        
        reader = new BufferedReader(new InputStreamReader(
            Channels.newInputStream(socketChannel), StandardCharsets.UTF_8));
        
        // Start background thread to read messages
        readerThread = new Thread(this::readMessages, "JSON-RPC Reader Thread");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Sends a JSON-RPC request and waits for the response.
     * Thread-safe.
     * 
     * @param method RPC method name
     * @param params Parameters as JsonNode
     * @return Response result as JsonNode
     * @throws ExecutorException if the request fails or returns an error
     * @throws InterruptedException if interrupted while waiting
     */
    public JsonNode sendRequest(String method, JsonNode params) 
            throws ExecutorException {
        if (closed) {
            throw new ExecutorException("Connection is closed");
        }
        
        int id = nextRequestId.incrementAndGet();        
        ResponseHandler handler = new ResponseHandler();
        pendingRequests.put(id, handler);
        
        try {
            writeToSocket(new RpcRequest(method, params, id));
            
            // Wait for response
            return handler.await();
        } catch (IOException e) {
            throw new ExecutorException("Failed to send request", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutorException("Interrupted while sending request", e);
        } finally {
            pendingRequests.remove(id);
        }
    }

    /**
     * Background thread that reads messages from the socket.
     */
    private void readMessages() {
        try {
            String line;
            while (!closed && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }
                
                try {
                    handleMessage(line);
                } catch (Exception e) {
                    logger.error("Error handling message {}", e, line);
                }
            }
        } catch (IOException e) {
            if (!closed) {
                logger.error("Error reading from socket", e);
            }
        }
    }

    /**
     * Handles an incoming message (either a response or a request).
     */
    private void handleMessage(String json) throws IOException {
        JsonNode message = objectMapper.readTree(json);
        
        JsonNode idNode = message.get("id");
        JsonNode methodNode = message.get("method");
        
        // Check if this is an incoming request from the remote side
        if (methodNode != null && !methodNode.isNull()) {
            handleIncomingRequest(message);
            return;
        }
        
        // It's a response to one of our requests
        if (idNode == null || idNode.isNull()) {
            logger.warn("Received response without ID: {}", json);
            return;
        }
        
        int id = idNode.asInt();
        ResponseHandler handler = pendingRequests.get(id);
        if (handler == null) {
            logger.warn("No pending request for response ID: {}", id);
            return;
        }
        
        JsonNode errorNode = message.get("error");
        if (errorNode != null && !errorNode.isNull()) {
            int code = errorNode.get("code").asInt();
            String errorMessage = errorNode.get("message").asText();
            handler.setError(new ExecutorException(code, errorMessage));
        } else {
            JsonNode result = message.get("result");
            handler.setResult(result);
        }
    }

    /**
     * Handles an incoming request from the remote side.
     */
    private void handleIncomingRequest(JsonNode message) {
        JsonNode idNode = message.get("id");
        if (idNode == null || idNode.isNull()) {
            logger.warn("Ignoring request without ID: {}", message.toString());
            return;
        }
        
        int requestId = idNode.asInt();
        JsonNode methodNode = message.get("method");
        JsonNode params = message.get("params");
        
        String method = methodNode.asText();
        
        // Create a response sender for this request
        RpcResponseSender responseSender = new RpcResponseSenderImpl(requestId);
        
        // Delegate to the request handler
        try {
            requestHandler.handleRequest(method, params, responseSender);
        } catch (ExecutorException e) {
            // ExecutorException == known error, send specific error code/message
            try {
                responseSender.sendError(e.getErrorCode(), e.getMessage());
            } catch (IOException | IllegalStateException ignored) {
                logger.error("Failed to send error response for request ID {}", requestId);
            }
        } catch (Exception e) {
            // If handler throws, send error response
            try {
                logger.error("Error handling request {}", e, message.toString());
                responseSender.sendError(-32603, "Internal error: " + e.getMessage());
            } catch (IOException | IllegalStateException ignored) {
                logger.error("Failed to send error response for request ID {}", requestId);
            }
        }
    }

    /**
     * Writes a JSON message directly to the socket channel.
     * Thread-safe.
     */
    private void writeToSocket(Object message) throws IOException {
        String json = objectMapper.writeValueAsString(message) + "\n";
        ByteBuffer buffer = ByteBuffer.wrap(json.getBytes(StandardCharsets.UTF_8));
        
        synchronized (socketChannel) {
            while (buffer.hasRemaining()) {
                socketChannel.write(buffer);
            }
        }
    }

    @Override
    public void close() throws ExecutorException {
        if (closed) {
            return;
        }
        
        closed = true;
        
        // Close socket (this will cause reader thread to exit)
        try {
            socketChannel.close();
        } catch (IOException e) {
            throw new ExecutorException("Failed to close socket", e);
        }
        
        // Wait for reader thread to finish
        try {
            readerThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Complete any pending requests with errors
        ExecutorException closedException = new ExecutorException("Connection closed");
        for (ResponseHandler handler : pendingRequests.values()) {
            handler.setError(closedException);
        }
        pendingRequests.clear();
    }

    /**
     * Implementation of RpcResponseSender that sends exactly one response.
     */
    private class RpcResponseSenderImpl implements RpcResponseSender {
        private final int requestId;
        private boolean sent = false;

        public RpcResponseSenderImpl(int requestId) {
            this.requestId = requestId;
        }

        @Override
        public synchronized void sendResponse(JsonNode result) throws IOException {
            if (sent) {
                throw new IllegalStateException("Response already sent");
            }
            sent = true;
            
            RpcResponse response = new RpcResponse(result, requestId);
            writeToSocket(response);
        }

        @Override
        public synchronized void sendError(int code, String message) throws IOException {
            if (sent) {
                throw new IllegalStateException("Response already sent");
            }
            sent = true;
            
            RpcResponse response = new RpcResponse(new RpcError(code, message), requestId);
            writeToSocket(response);
        }
    }

    /**
     * Helper class to handle request/response synchronization.
     */
    private static class ResponseHandler {
        private JsonNode result;
        private ExecutorException error;
        private boolean complete = false;

        public synchronized JsonNode await() throws InterruptedException, ExecutorException {
            while (!complete) {
                wait();
            }
            
            if (error != null) {
                throw error;
            }
            
            return result;
        }

        public synchronized void setResult(JsonNode result) {
            this.result = result;
            this.complete = true;
            notifyAll();
        }

        public synchronized void setError(ExecutorException error) {
            this.error = error;
            this.complete = true;
            notifyAll();
        }
    }
}
