// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

/**
 * Thrown when TLS is configured for a connector but cannot be satisfied -- a missing certificate, an
 * unreadable key store, no acceptable protocol in common, and so on.
 * <p>
 * Connectors let this fail the deploy rather than catching it. Falling back to an unencrypted
 * connection because the requested TLS configuration could not be built would silently downgrade a
 * channel the user asked to secure.
 */
public class TlsConfigurationException extends Exception {

    private static final long serialVersionUID = 1L;

    public TlsConfigurationException(String message) {
        super(message);
    }

    public TlsConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
