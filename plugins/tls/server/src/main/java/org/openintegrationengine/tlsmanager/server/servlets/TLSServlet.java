/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server.servlets;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.connectors.http.HttpDispatcherProperties;
import com.mirth.connect.connectors.tcp.TcpDispatcherProperties;
import com.mirth.connect.connectors.ws.DefinitionServiceMap;
import com.mirth.connect.connectors.ws.WebServiceDispatcherProperties;
import com.mirth.connect.server.api.DontCheckAuthorized;
import com.mirth.connect.server.api.MirthServlet;
import lombok.extern.slf4j.Slf4j;
import org.openintegrationengine.tlsmanager.server.CertificateService;
import org.openintegrationengine.tlsmanager.server.TLSServicePlugin;
import org.openintegrationengine.tlsmanager.server.WebServiceService;
import org.openintegrationengine.tlsmanager.shared.TLSPluginConstants;
import org.openintegrationengine.tlsmanager.shared.models.ConnectionTestResult;
import org.openintegrationengine.tlsmanager.shared.models.LocalCertificate;
import org.openintegrationengine.tlsmanager.shared.models.TrustedCertificate;
import org.openintegrationengine.tlsmanager.shared.servlet.TLSServletInterface;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Set;

@Slf4j
@MirthApiProvider(type = ApiProviderType.SERVER_CLASS)
public class TLSServlet extends MirthServlet implements TLSServletInterface {

    private final CertificateService certificateService;
    private final WebServiceService webServiceService;

    public TLSServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        this(
            request,
            sc,
            TLSServicePlugin.getPluginInstance().getCertificateService(),
            TLSServicePlugin.getPluginInstance().getWebServiceService()
        );
    }

    public TLSServlet(
        @Context HttpServletRequest request,
        @Context SecurityContext sc,
        CertificateService certificateService,
        WebServiceService webServiceService
    ) {
        super(request, sc, TLSPluginConstants.PLUGIN_POINTNAME);

        this.certificateService = certificateService;
        this.webServiceService = webServiceService;
    }

    @Override
    public Set<String> getPublicCertificates() {
        return certificateService.getTrustedCertificateAliases();
    }

    @Override
    public Set<String> getClientCertificates() {
        return certificateService.getLocalCertificateAliases();
    }

    @Override
    public byte[] getKeystore() {
        var keystore = certificateService.getExternalTrustStore();

        try (var baos = new ByteArrayOutputStream()) {
            keystore.store(baos, "changeit".toCharArray());
            return baos.toByteArray();
        } catch (IOException | NoSuchAlgorithmException | CertificateException | KeyStoreException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @DontCheckAuthorized
    public String setTruststore(InputStream inputStream, String password) {

        if (!isUserAuthorized(false)) {
            isUserAuthorized(true);
            throw new WebApplicationException(Response.Status.FORBIDDEN);
        }

        byte[] trustStoreBytes;
        try {
            trustStoreBytes = inputStream.readAllBytes();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        certificateService.storeExtraTrustStore(trustStoreBytes, password.toCharArray());
        return "timmis";
    }

    @Override
    public List<TrustedCertificate> getSystemCertificates() {
        return certificateService.getEncodedSystemCertificates();
    }

    @Override
    public List<TrustedCertificate> getLocalCertificates() {
        return certificateService.getEncodedLocalCertificates();
    }

    @Override
    public void setLocalCertificates(List<LocalCertificate> localCertificates) {
        certificateService.setLocalCertificates(localCertificates);
    }

    @Override
    public List<TrustedCertificate> getTrustedCertificates() {
        return certificateService.getEncodedTrustedCertificates();
    }

    @Override
    public void setTrustedCertificates(List<TrustedCertificate> trustedCertificates) {
        certificateService.setTrustedCertificates(trustedCertificates);
    }

    @Override
    public List<TrustedCertificate> getRemoteCertificates(String url) {
        return certificateService.retrieveRemoteCertificates(url);
    }

    @Override
    public ConnectionTestResult testTcpConnection(String channelId, String channelName, TcpDispatcherProperties dispatcherProperties) throws ClientException {
        return certificateService.testTcpConnection(channelId, channelName, dispatcherProperties);
    }

    @Override
    public ConnectionTestResult testHttpsConnection(String channelId, String channelName, HttpDispatcherProperties dispatcherProperties) throws ClientException {
        return certificateService.testHttpConnection(channelId, channelName, dispatcherProperties);
    }

    @Override
    public ConnectionTestResult testWsConnection(String channelId, String channelName, WebServiceDispatcherProperties wsDispatcherProperties) throws ClientException {
        return certificateService.testWsConnection(channelId, channelName, wsDispatcherProperties);
    }

    @Override
    public Object cacheWsdlFromUrl(
        String channelId,
        String channelName,
        WebServiceDispatcherProperties properties
    ) {
        try {
            webServiceService.cacheWsdlFromUrl(channelId, channelName, properties);
            return null;
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }

    @Override
    public boolean isWsdlCached(
        String channelId,
        String channelName,
        String wsdlUrl,
        String username,
        String password
    ) {
        return false;
    }

    @Override
    public DefinitionServiceMap getDefinition(
        String channelId,
        String channelName,
        String wsdlUrl,
        String username,
        String password
    ) {
        try {
            return webServiceService.getDefinition(channelId, channelName, wsdlUrl, username, password);
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }
}
