/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.properties;

import com.mirth.connect.donkey.model.channel.ConnectorPluginProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.openintegrationengine.tlsmanager.shared.models.RevocationMode;
import org.openintegrationengine.tlsmanager.shared.models.SubjectDnValidationMode;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

@Getter
@Setter
@ToString
public abstract class AbstractTLSConnectorProperties extends ConnectorPluginProperties {
    protected boolean isTlsManagerEnabled;

    protected boolean trustSystemTruststore;
    protected Set<String> trustedServerCertificates;

    // Certificate revocation modes
    protected RevocationMode crlMode;
    protected RevocationMode ocspMode;

    protected SubjectDnValidationMode subjectDnValidationMode;
    protected String subjectDnValidationFilter;

    // Protocols
    protected boolean isUseServerDefaultProtocols;
    protected Set<String> usedProtocols;

    // Ciphers
    protected boolean isUseServerDefaultCiphers;
    protected Set<String> usedCiphers;

    protected AbstractTLSConnectorProperties() {
        isTlsManagerEnabled = false;

        trustSystemTruststore = true;
        trustedServerCertificates = Collections.emptySet();

        subjectDnValidationMode = SubjectDnValidationMode.NONE;
        subjectDnValidationFilter = null;

        crlMode = RevocationMode.HARD_FAIL;
        ocspMode = RevocationMode.HARD_FAIL;

        isUseServerDefaultProtocols = true;
        usedProtocols = Collections.emptySet();

        isUseServerDefaultCiphers = true;
        usedCiphers = Collections.emptySet();
    }

    protected AbstractTLSConnectorProperties(AbstractTLSConnectorProperties props) {
        isTlsManagerEnabled = props.isTlsManagerEnabled();

        trustSystemTruststore = props.isTrustSystemTruststore();
        trustedServerCertificates = Objects.requireNonNullElse(
            props.getTrustedServerCertificates(),
            Collections.emptySet()
        );

        subjectDnValidationMode = Objects.requireNonNullElse(
            props.getSubjectDnValidationMode(),
            SubjectDnValidationMode.NONE
        );
        subjectDnValidationFilter = props.getSubjectDnValidationFilter();

        crlMode = Objects.requireNonNullElse(props.getCrlMode(), RevocationMode.HARD_FAIL);
        ocspMode = Objects.requireNonNullElse(props.getOcspMode(), RevocationMode.HARD_FAIL);

        isUseServerDefaultProtocols = props.isUseServerDefaultProtocols();
        usedProtocols = Objects.requireNonNullElse(
            props.getUsedProtocols(),
            Collections.emptySet()
        );

        isUseServerDefaultCiphers = props.isUseServerDefaultCiphers();
        usedCiphers = Objects.requireNonNullElse(
            props.getUsedCiphers(),
            Collections.emptySet()
        );
    }
}
