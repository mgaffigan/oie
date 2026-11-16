// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * A fully-resolved TLS configuration for one connector deployment.
 * <p>
 * The {@link SSLContext} is returned ready to use: it already holds the key material this side
 * presents and the trust material used to validate the peer. The engine never selects a certificate
 * alias and never composes trust, so an implementation is free to decide those however it likes --
 * including inside a {@link javax.net.ssl.X509ExtendedTrustManager}, which is the only place
 * stapled OCSP responses are reachable.
 * <p>
 * Everything that cannot be expressed inside an {@code SSLContext} is carried alongside it, because
 * each transport applies those settings through a different API.
 */
public interface TlsContext {

    /** The initialised context. Never null. */
    SSLContext getSslContext();

    /** Protocols to enable, already intersected with what the JVM supports. Never null or empty. */
    String[] getEnabledProtocols();

    /** Cipher suites to enable, already intersected with what the JVM supports. Never null or empty. */
    String[] getEnabledCipherSuites();

    /** What to ask of the peer. Meaningful only for {@link TlsRole#SERVER}. Never null. */
    ClientAuth getClientAuth();

    /**
     * Whether the peer's certificate must match the host being connected to. Meaningful only for
     * {@link TlsRole#CLIENT}.
     */
    boolean isEndpointIdentificationEnabled();

    /**
     * An explicit verifier for stacks that take one, such as Apache HttpClient and JAX-WS. May be
     * null, in which case callers derive the behaviour from
     * {@link #isEndpointIdentificationEnabled()}. Returning a verifier here overrides that flag for
     * those stacks, which is how an implementation expresses a verification rule the standard
     * algorithm cannot.
     */
    HostnameVerifier getHostnameVerifier();

    /**
     * Applies protocols, cipher suites and the role-appropriate peer settings to {@code parameters}.
     * Use this wherever the transport exposes {@link SSLParameters} directly, so that every
     * transport ends up applying the same policy the same way.
     */
    default void applyTo(SSLParameters parameters, TlsRole role) {
        parameters.setProtocols(getEnabledProtocols());
        parameters.setCipherSuites(getEnabledCipherSuites());

        if (role == TlsRole.SERVER) {
            parameters.setWantClientAuth(getClientAuth() == ClientAuth.WANT);
            parameters.setNeedClientAuth(getClientAuth() == ClientAuth.NEED);
        } else {
            parameters.setEndpointIdentificationAlgorithm(isEndpointIdentificationEnabled() ? "HTTPS" : null);
        }
    }
}
