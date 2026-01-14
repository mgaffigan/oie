/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.revocation;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.openintegrationengine.tlsmanager.server.util.TrustStoreUtils;
import org.openintegrationengine.tlsmanager.shared.models.RevocationMode;
import org.openintegrationengine.tlsmanager.shared.models.SubjectDnValidationMode;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.security.auth.x500.X500Principal;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CRL;
import java.security.cert.CertPath;
import java.security.cert.CertPathValidator;
import java.security.cert.CertStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.CollectionCertStoreParameters;
import java.security.cert.PKIXParameters;
import java.security.cert.PKIXRevocationChecker;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public final class DualCheckerTrustManager extends X509ExtendedTrustManager {

    private final KeyStore effectiveTrustStore;
    private final KeyStore systemTrustStore;
    private final SubjectDnValidationMode subjectDnValidationMode;
    private final String subjectDnValidationFilter;
    private final RevocationMode ocspMode, crlMode;
    private final Collection<? extends CRL> preloadedCrls; // optional (in addition to CRLDP)

    private final Set<X509Certificate> trustedLeafCertSet;
    private final Set<X509Certificate> trustedCASet;
    private final Set<TrustAnchor> trustAnchorsSet;

    private final X509Certificate[] acceptedIssuers;

    private final X509ExtendedTrustManager trustManagerDelegate;

    private final CertificateFactory certificateFactory;
    private final CertPathValidator certPathValidator;

    public DualCheckerTrustManager(
        KeyStore effectiveTrustStore,
        KeyStore systemTrustStore,
        SubjectDnValidationMode subjectDnValidationMode,
        String subjectDnValidationFilter,
        RevocationMode ocspMode,
        RevocationMode crlMode,
        Collection<? extends CRL> preloadedCrls,
        Set<String> trustedAliasSet
    ) {
        this.effectiveTrustStore = effectiveTrustStore;
        this.systemTrustStore = systemTrustStore;
        this.subjectDnValidationMode = subjectDnValidationMode;
        this.subjectDnValidationFilter = subjectDnValidationFilter;
        this.ocspMode = ocspMode;
        this.crlMode = crlMode;
        this.preloadedCrls = preloadedCrls == null ? List.of() : preloadedCrls;

        this.trustedLeafCertSet = new HashSet<>();
        this.trustedCASet = new HashSet<>();
        this.trustAnchorsSet = new HashSet<>();
        initTrustedLeafSet(Objects.requireNonNullElse(trustedAliasSet, Set.of()));

        try {
            this.certificateFactory = CertificateFactory.getInstance("X.509");
            this.certPathValidator = CertPathValidator.getInstance("PKIX");

            var tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(systemTrustStore);

            trustManagerDelegate = Arrays.stream(tmf.getTrustManagers())
                .filter(X509ExtendedTrustManager.class::isInstance)
                .map(X509ExtendedTrustManager.class::cast)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No default X509ExtendedTrustManager found"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize TrustManager", e);
        }

        // Pre-render accepted issuers array
        var acceptedIssuers = new ArrayList<X509Certificate>();
        if (systemTrustStore != null) {
            acceptedIssuers.addAll(List.of(trustManagerDelegate.getAcceptedIssuers()));
        }
        acceptedIssuers.addAll(trustedCASet);
        this.acceptedIssuers = acceptedIssuers.toArray(new X509Certificate[0]);
    }

    // --- JSSE delegation ---
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            runValidations(chain, authType, null, null, true);
        } catch (CertificateException e) {
            log.error("Failed to check client trust", e);
            throw e;
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) throws CertificateException {
        try {
            runValidations(chain, authType, socket, null, true);
        } catch (CertificateException e) {
            log.error("Failed to check client trust", e);
            throw e;
        }
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        try {
            runValidations(chain, authType, null, sslEngine, true);
        } catch (CertificateException e) {
            log.error("Failed to check client trust", e);
            throw e;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        try {
            runValidations(chain, authType, null, null, false);
        } catch (CertificateException e) {
            log.error("Failed to check server trust", e);
            throw e;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket s) throws CertificateException {
        try {
            runValidations(chain, authType, s, null, false);
        } catch (CertificateException e) {
            log.error("Failed to check server trust", e);
            throw e;
        }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine sslEngine) throws CertificateException {
        try {
            runValidations(chain, authType, null , sslEngine, false);
        } catch (CertificateException e) {
            log.error("Failed to check server trust", e);
            throw e;
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return this.acceptedIssuers;
    }

    private boolean hasStapledOcsp(X509Certificate[] chain, SSLSession session) throws CertificateException {
        if (session instanceof ExtendedSSLSession extendedSession) {
            var statusResponses = extendedSession.getStatusResponses();

            if (statusResponses != null && !statusResponses.isEmpty()) {
                log.info("Received {} stapled OCSP response(s)", statusResponses.size());

                for (int i = 0; i < Math.min(statusResponses.size(), chain.length); i++) {
                    byte[] response = statusResponses.get(i);
                    if (response != null && response.length > 0) {
                        validateStapledOcspResponse(response, chain[i]);
                        return true;
                    }
                }
            }
        } else {
            log.debug("SSLSession is not an ExtendedSSLSession");
        }

        return false;
    }

    private void initTrustedLeafSet(Set<String> trustedAliasSet) {
        trustedAliasSet.forEach(alias -> {
            try {
                var cert = effectiveTrustStore.getCertificate(alias);
                if (cert instanceof X509Certificate x509Certificate) {
                    if (TrustStoreUtils.isCA(x509Certificate)) {
                        trustedCASet.add(x509Certificate);
                    } else {
                        trustedLeafCertSet.add(x509Certificate);
                    }
                } else {
                    log.debug("Truststore does not contain x509Certificate with alias {}", alias);
                }
            } catch (KeyStoreException e) {
                throw new RuntimeException(e);
            }
        });

        // Prerender trust anchors set
        var anchors = trustedCASet
            .stream()
            .map(x509Certificate -> new TrustAnchor(x509Certificate, null))
            .collect(Collectors.toSet());
        this.trustAnchorsSet.addAll(anchors);
    }

    private boolean isCertIssuedByTrustedCA(X509Certificate cert) {
        try {
            var certPath = this.certificateFactory.generateCertPath(List.of(cert));
            var params = new PKIXParameters(this.trustAnchorsSet);
            params.setRevocationEnabled(false);

            certPathValidator.validate(certPath, params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isCertIssuedBySystemCAServer(X509Certificate[] chain, String authType, Socket socket, SSLEngine sslEngine) {
        try {
            if (socket != null) {
                trustManagerDelegate.checkServerTrusted(chain, authType, socket);
            } else if (sslEngine != null) {
                trustManagerDelegate.checkServerTrusted(chain, authType, sslEngine);
            } else {
                trustManagerDelegate.checkServerTrusted(chain, authType);
            }
            return true;
        } catch (CertificateException e) {
            log.debug("Certificate chain not trusted by system cacerts", e);
            return false;
        }
    }

    private boolean isCertIssuedBySystemCAClient(X509Certificate[] chain, String authType, Socket socket, SSLEngine sslEngine) {
        try {
            if (socket != null) {
                trustManagerDelegate.checkClientTrusted(chain, authType, socket);
            } else if (sslEngine != null) {
                trustManagerDelegate.checkClientTrusted(chain, authType, sslEngine);
            } else {
                trustManagerDelegate.checkClientTrusted(chain, authType);
            }
            return true;
        } catch (CertificateException e) {
            log.debug("Certificate chain not trusted by system cacerts", e);
            return false;
        }
    }

    private void runValidations(X509Certificate[] chain, String authType, Socket socket, SSLEngine sslEngine, boolean checkClientTrust) throws CertificateException {
        var serverChain = List.of(chain);

        boolean isCertTrusted = false;
        CertificateException lastException = null;

        // Handle system cacerts
        if (systemTrustStore != null) {
            if (checkClientTrust) {
                if (isCertIssuedBySystemCAClient(chain, authType, socket, sslEngine)) {
                    isCertTrusted = true;
                } else {
                    log.debug("Truststore does not contain system certificates to validate the remote leaf certificate");
                    lastException = new CertificateException("Remote leaf certificate is not trusted");
                }
            } else {
                if (isCertIssuedBySystemCAServer(chain, authType, socket, sslEngine)) {
                    isCertTrusted = true;
                } else {
                    log.debug("Truststore does not contain system certificates to validate the remote leaf certificate");
                    lastException = new CertificateException("Remote leaf certificate is not trusted");
                }
            }
        }

        // Check for leaf certs
        var leafCert = serverChain.get(0);
        if (!isCertTrusted && trustedLeafCertSet.contains(leafCert)) {
            isCertTrusted = true;
        } else {
            log.debug("Truststore does not contain leaf certificate");
            lastException = new CertificateException("Remote leaf certificate is not trusted");
        }

        // Check for intermediate CA certs
        if (!isCertTrusted) {
            if (isCertIssuedByTrustedCA(leafCert)) {
                isCertTrusted = true;
            } else {
                log.debug("Truststore does not contain CA certs to validate the remote leaf certificate");
                lastException = new CertificateException("Remote leaf certificate is not trusted");
            }
        }

        // Throw is no mechanism succeeded
        if (!isCertTrusted) {
            throw lastException;
        }

        try {
            if (subjectDnValidationMode != null && subjectDnValidationMode != SubjectDnValidationMode.NONE) {
                if (subjectDnValidationFilter == null || subjectDnValidationFilter.isEmpty()) {
                    throw new IllegalStateException("Expected Subject DN cannot be empty");
                }

                var subject = chain[0].getSubjectX500Principal();

                var subjectDn = subject.getName(X500Principal.RFC2253);
                var expectedDn = new X500Principal(subjectDnValidationFilter).getName(X500Principal.RFC2253);
                if (subjectDnValidationMode == SubjectDnValidationMode.EXACT) {
                    if (!subjectDn.equals(expectedDn)) {
                        throw new CertificateException("Subject DN does not match filter");
                    }
                } else if (subjectDnValidationMode == SubjectDnValidationMode.PARTIAL) {
                    LdapName subjectLdapName, expectedLdapName;
                    try {
                        subjectLdapName = new LdapName(subjectDn);
                        expectedLdapName = new LdapName(expectedDn);
                    } catch (InvalidNameException e) {
                        throw new CertificateException("Error converting DN to LdapName", e);
                    }

                    var subjectRdns = subjectLdapName.getRdns();
                    for (var expectedRdn : expectedLdapName.getRdns()) {
                        if (!subjectRdns.contains(expectedRdn)) {
                            throw new CertificateException("Subject DN does not contain expected RDN");
                        }
                    }
                } else {
                    throw new CertificateException("Unsupported SubjectDnValidationMode: " + subjectDnValidationMode);
                }
            }

            var certPath = certificateFactory.generateCertPath(serverChain);

            // OCSP-only pass (if requested)
            if (ocspMode != RevocationMode.DISABLED) {
                if (socket != null || sslEngine != null) {
                    SSLSession session;

                    if (socket instanceof SSLSocket sslSocket) {
                        session = sslSocket.getHandshakeSession();
                    } else if (sslEngine != null) {
                        session = sslEngine.getSession();
                    } else {
                        throw new IllegalStateException("Expected either a Socket or SSLEngine");
                    }

                    var hasStapledOcsp = hasStapledOcsp(chain, session);
                    if (!hasStapledOcsp) {
                        pkixOcspOnly(certPath, ocspMode == RevocationMode.SOFT_FAIL);
                    }
                }
            }

            // CRL-only pass (if requested)
            if (crlMode != RevocationMode.DISABLED) {
                // Preloaded CRLs + CRLDP-fetched CRLs (HTTP)
                List<CRL> crls = new ArrayList<>(preloadedCrls);
                crls.addAll(fetchCrlsFromCrlDP(chain));
                pkixCrlOnly(certPath, crls, crlMode == RevocationMode.SOFT_FAIL);
            }
            // If both are HARD_FAIL, reaching here means both passes succeeded.

        } catch (GeneralSecurityException e) {
            if (e instanceof CertificateException exception) {
                throw exception;
            }

            throw new CertificateException("Validation error: " + e.getMessage(), e);
        }
    }

    // ---- Pass A: OCSP-only ----
    private void pkixOcspOnly(CertPath path, boolean softFail) throws GeneralSecurityException {
        var params = new PKIXParameters(this.trustAnchorsSet);
        params.setRevocationEnabled(true);

        var revocationChecker = (PKIXRevocationChecker) certPathValidator.getRevocationChecker();

        var opts = EnumSet.of(
            PKIXRevocationChecker.Option.NO_FALLBACK // don't fall back to CRLs
        );

        if (softFail) opts.add(PKIXRevocationChecker.Option.SOFT_FAIL);

        revocationChecker.setOptions(opts);
        params.addCertPathChecker(revocationChecker);

        certPathValidator.validate(path, params);
    }

    // ---- Pass B: CRL-only ----
    private void pkixCrlOnly(CertPath path, Collection<? extends CRL> crls, boolean softFail) throws GeneralSecurityException {
        var params = new PKIXParameters(this.trustAnchorsSet);
        params.setRevocationEnabled(true);

        if (crls != null && !crls.isEmpty()) {
            CertStore cs = CertStore.getInstance("Collection", new CollectionCertStoreParameters(crls));
            params.addCertStore(cs);
        }

        var revocationChecker = (PKIXRevocationChecker) certPathValidator.getRevocationChecker();

        var opts = EnumSet.of(
            PKIXRevocationChecker.Option.PREFER_CRLS, // CRL-first
            PKIXRevocationChecker.Option.NO_FALLBACK  // do NOT fall back to OCSP
        );

        if (softFail) {
            opts.add(PKIXRevocationChecker.Option.SOFT_FAIL);
        }

        revocationChecker.setOptions(opts);
        params.addCertPathChecker(revocationChecker);

        certPathValidator.validate(path, params);
    }

    private Collection<? extends CRL> fetchCrlsFromCrlDP(X509Certificate[] chain) {
        List<CRL> out = new ArrayList<>();
        try {
            for (X509Certificate cert : chain) {
                byte[] ext = cert.getExtensionValue(Extension.cRLDistributionPoints.getId());
                if (ext == null) continue;

                byte[] inner = ((DEROctetString) ASN1Primitive.fromByteArray(ext)).getOctets();

                var crlDistPoint = CRLDistPoint.getInstance(ASN1Primitive.fromByteArray(inner));
                for (DistributionPoint p : crlDistPoint.getDistributionPoints()) {
                    var name = p.getDistributionPoint();
                    if (name == null || name.getType() != DistributionPointName.FULL_NAME) continue;

                    for (GeneralName gn : GeneralNames.getInstance(name.getName()).getNames()) {
                        if (gn.getTagNo() == GeneralName.uniformResourceIdentifier) {
                            String uri = gn.getName().toString();
                            if (!uri.startsWith("http://")) continue; // keep simple; avoid HTTPS recursion

                            try (InputStream in = URI.create(uri).toURL().openStream()) {
                                byte[] bytes = in.readAllBytes();
                                byte[] der = maybeDecodePem(bytes, "X509 CRL");
                                out.add(certificateFactory.generateCRL(new ByteArrayInputStream(der)));
                            } catch (Exception ignoreOne) {}
                        }
                    }
                }
            }
        } catch (Exception ignore) {}
        return out;
    }

    private static byte[] maybeDecodePem(byte[] content, String type) {
        String s = new String(content);
        if (!s.contains("-----BEGIN " + type)) return content;
        String base64 = s.replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private void validateStapledOcspResponse(byte[] responseBytes, X509Certificate cert)
        throws CertificateException {
        try {
            var ocspResponse = new OCSPResp(responseBytes);

            if (ocspResponse.getStatus() == OCSPResp.SUCCESSFUL) {
                var basicResponse = (BasicOCSPResp) ocspResponse.getResponseObject();

                for (var singleResp : basicResponse.getResponses()) {
                    var status = singleResp.getCertStatus();

                    if (status == CertificateStatus.GOOD) {
                        log.debug("Stapled OCSP: Certificate is GOOD");
                    } else if (status instanceof RevokedStatus) {
                        throw new CertificateException("Certificate is REVOKED (from stapled OCSP)");
                    } else {
                        log.warn("Stapled OCSP: Certificate status is UNKNOWN");
                    }
                }
            } else {
                log.warn("Stapled OCSP response status: {}", ocspResponse.getStatus());
            }
        } catch (Exception e) {
            log.error("Failed to validate stapled OCSP response", e);
            if (ocspMode == RevocationMode.HARD_FAIL) {
                throw new CertificateException("Invalid stapled OCSP response", e);
            }
        }
    }
}
