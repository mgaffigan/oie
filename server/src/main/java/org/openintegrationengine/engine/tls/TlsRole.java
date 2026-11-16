// SPDX-License-Identifier: MPL-2.0
// SPDX-FileCopyrightText: Mitch Gaffigan <mitch@gaffigan.net>

package org.openintegrationengine.engine.tls;

/**
 * Which side of the TLS handshake a connector deployment is on.
 * <p>
 * This is a property of the deployment, not of the connector type: a TCP connector presents a
 * server certificate when deployed in server mode and a client certificate otherwise, in both the
 * source and destination directions. Always take the role from
 * {@link TlsCapableConnectorPropertiesInterface#getTlsRole()} rather than inferring it from the
 * transport name or from whether the connector is a source or a destination.
 */
public enum TlsRole {
    /** This side initiates the connection and validates the certificate the peer presents. */
    CLIENT,

    /** This side accepts connections and presents a certificate to the peer. */
    SERVER
}
