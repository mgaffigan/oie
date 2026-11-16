// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

/**
 * What a TLS server asks of its peer. Meaningful only for {@link TlsRole#SERVER}.
 * <p>
 * This cannot be folded into the {@link javax.net.ssl.SSLContext}, because each listener stack
 * applies it through a different API: Jetty's {@code SslContextFactory}, the JDK's
 * {@code HttpsParameters}, and {@code SSLServerSocket} all want their own want/need pair.
 */
public enum ClientAuth {
    /** The peer is not asked for a certificate. */
    NONE,

    /** The peer is asked for a certificate, but the handshake succeeds without one. */
    WANT,

    /** The peer must present an acceptable certificate or the handshake fails. */
    NEED
}
