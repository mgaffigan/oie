/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.models;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;

public record WeirdIntermediaryContextContainer (
    SSLContext sslContext,
    String[] protocols,
    String[] ciphers,
    HostnameVerifier hostnameVerifier
) {}
