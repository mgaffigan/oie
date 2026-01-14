/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.util;

import java.security.cert.X509Certificate;

public class TrustStoreUtils {

    /**
     * Determines whether the given X.509 certificate is a Certificate Authority (CA)
     * certificate based on its BasicConstraints extension.
     *
     * <p>A certificate is considered a CA certificate if its
     * {@link X509Certificate#getBasicConstraints()} value is greater than or equal
     * to zero. According to RFC 5280, the BasicConstraints extension must be present
     * and set to a non-negative path length value for a certificate to be treated
     * as a CA. A return value of {@code -1} indicates that the certificate is not a
     * CA (i.e., it is an end-entity or leaf certificate).</p>
     *
     * <p>This method does not examine the KeyUsage extension; it relies solely on
     * BasicConstraints, which is the authoritative indicator for CA certificates.</p>
     *
     * @param cert the X.509 certificate to evaluate (must not be {@code null})
     * @return {@code true} if the certificate is a CA certificate;
     *         {@code false} if it is a leaf/end-entity certificate
     */
    public static boolean isCA(X509Certificate cert) {
        return cert.getBasicConstraints() >= 0;
    }
}
