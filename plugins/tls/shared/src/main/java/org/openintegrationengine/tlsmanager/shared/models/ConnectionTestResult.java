/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import lombok.Builder;
import lombok.Getter;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.List;

@Builder
@Getter
public final class ConnectionTestResult {
    private Boolean success;
    private String message;
    private Instant timestamp;
    private String requestedAddress;

    private String protocol;
    private String cipherSuite;
    private String sessionId;
    private String peerHost;
    private Integer peerPort;
    private Boolean sessionValid;
    private Instant sessionCreationTime;
    private Instant sessionLastAccessedTime;

    private List<String> supportedProtocols;
    private List<String> enabledProtocols;
    private String chosenProtocol;

    private List<String> supportedCipherSuites;
    private List<String> enabledCipherSuites;
    private String chosenCipherSuite;

    private List<Certificate> certificates;

    private String exceptionName;
    private String exceptionMessage;
    private String causeName;
    private String causeMessage;

    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";

        var hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X", b));
        }
        return hexString.toString();
    }

    public static int getKeySize(X509Certificate cert) {
        try {
            if (cert.getPublicKey().getAlgorithm().equals("RSA")) {
                return ((java.security.interfaces.RSAPublicKey) cert.getPublicKey()).getModulus().bitLength();
            } else if (cert.getPublicKey().getAlgorithm().equals("EC")) {
                return ((java.security.interfaces.ECPublicKey) cert.getPublicKey()).getParams().getOrder().bitLength();
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }

    public static String getCertificateFingerprint(X509Certificate cert, String algorithm) {
        try {
            var md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(cert.getEncoded());
            var sb = new StringBuilder();
            for (int i = 0; i < digest.length; i++) {
                if (i > 0) sb.append(":");
                sb.append(String.format("%02X", digest[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return "Unable to calculate fingerprint";
        }
    }
}
