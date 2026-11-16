// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mirth Corporation
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package com.mirth.connect.util;

import java.util.List;

public class ConnectionTestResponse {
    public enum Type {
        SUCCESS, TIME_OUT, FAILURE
    }

    private Type type;
    private String message;
    private String connectionInfo;

    /*
     * Set only when the test completed a TLS handshake, so that the result distinguishes "the port
     * is open" from "the port is open and we agreed on these terms with this peer".
     */
    private String tlsProtocol;
    private String tlsCipherSuite;
    private List<String> peerCertificateSubjects;

    public ConnectionTestResponse(Type type, String message) {
        this(type, message, null);
    }

    public ConnectionTestResponse(Type type, String message, String connectionInfo) {
        this.type = type;
        this.message = message;
        this.connectionInfo = connectionInfo;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getConnectionInfo() {
        return connectionInfo;
    }

    public void setConnectionInfo(String connectionInfo) {
        this.connectionInfo = connectionInfo;
    }

    /** The negotiated protocol, or null if the test did not perform a TLS handshake. */
    public String getTlsProtocol() {
        return tlsProtocol;
    }

    public void setTlsProtocol(String tlsProtocol) {
        this.tlsProtocol = tlsProtocol;
    }

    /** The negotiated cipher suite, or null if the test did not perform a TLS handshake. */
    public String getTlsCipherSuite() {
        return tlsCipherSuite;
    }

    public void setTlsCipherSuite(String tlsCipherSuite) {
        this.tlsCipherSuite = tlsCipherSuite;
    }

    /** Subject names from the peer's chain, leaf first, or null if no handshake was performed. */
    public List<String> getPeerCertificateSubjects() {
        return peerCertificateSubjects;
    }

    public void setPeerCertificateSubjects(List<String> peerCertificateSubjects) {
        this.peerCertificateSubjects = peerCertificateSubjects;
    }
}