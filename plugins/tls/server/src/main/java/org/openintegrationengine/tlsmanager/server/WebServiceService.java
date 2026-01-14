/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.server;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.connectors.ws.DefinitionServiceMap;
import com.mirth.connect.connectors.ws.WebServiceDispatcherProperties;
import com.mirth.connect.server.util.TemplateValueReplacer;
import lombok.extern.slf4j.Slf4j;
import org.apache.cxf.wsdl11.WSDLManagerImpl;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.openintegrationengine.tlsmanager.shared.properties.TLSConnectorProperties;

import javax.wsdl.BindingOperation;
import javax.wsdl.Definition;
import javax.wsdl.Port;
import javax.wsdl.Service;
import javax.wsdl.extensions.ExtensibilityElement;
import javax.wsdl.extensions.http.HTTPAddress;
import javax.wsdl.extensions.http.HTTPOperation;
import javax.wsdl.extensions.soap.SOAPAddress;
import javax.wsdl.extensions.soap.SOAPOperation;
import javax.wsdl.extensions.soap12.SOAP12Address;
import javax.wsdl.extensions.soap12.SOAP12Operation;
import javax.xml.namespace.QName;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class WebServiceService {
    private final SocketFactoryService socketFactoryService;
    private final TemplateValueReplacer templateValueReplacer;

    private static final Map<String, DefinitionServiceMap> definitionCache = new HashMap<>();
    private static final Map<String, Map<String, Map<String, Definition>>> wsdlInterfaceCache = new HashMap<>();

    public WebServiceService() {
        // This looks ugly, I know
        this(
            TLSServicePlugin.getPluginInstance().getSocketFactoryService(),
            new TemplateValueReplacer()
        );
    }

    public WebServiceService(
        SocketFactoryService socketFactoryService,
        TemplateValueReplacer templateValueReplacer
    ) {
        this.socketFactoryService = socketFactoryService;
        this.templateValueReplacer = templateValueReplacer;
    }

    public void cacheWsdlFromUrl(
        String channelId,
        String channelName,
        WebServiceDispatcherProperties properties
    ) throws Exception {
        var wsdlLocation = getWsdlUrl(
            channelId,
            channelName,
            properties.getWsdlUrl(),
            properties.getUsername(),
            properties.getPassword()
        );

        var connectorProperties = getTlsSenderProperties(properties);
        var socketFactory = socketFactoryService.getConnectorSocketFactory(connectorProperties);

        int timeout = 30_000; // 30 seconds
        var config = RequestConfig.custom()
            .setConnectTimeout(timeout)
            .setConnectionRequestTimeout(timeout)
            .setSocketTimeout(timeout).build();

        var clientBuilder = HttpClients.custom()
            .setDefaultRequestConfig(config)
            .setSSLSocketFactory(socketFactory);

        File tempWsdlFile;
        try (var httpClient = clientBuilder.build()) {
            var wsdlGet = new HttpGet(wsdlLocation);
            var response = httpClient.execute(wsdlGet);

            tempWsdlFile = File.createTempFile("wsdl", ".tmp");
            log.debug("Writing WSDL to {}", tempWsdlFile.getAbsolutePath());
            try (var fos = new FileOutputStream(tempWsdlFile)) {
                response.getEntity().writeTo(fos);
            }
        }

        var wsdlManager = new WSDLManagerImpl();
        var definition = wsdlManager.getDefinition(tempWsdlFile.getAbsolutePath());
        cacheWsdlInterfaces(wsdlLocation, definition);
    }

    public DefinitionServiceMap getDefinition(
        String channelId,
        String channelName,
        String wsdlUrl,
        String username,
        String password
    ) {
        try {
            var wsdlLocation = getWsdlUrl(channelId, channelName, wsdlUrl, username, password);
            var definition = definitionCache.get(wsdlLocation);
            if (definition == null) {
                throw new Exception("WSDL not cached for URL: " + wsdlLocation);
            }
            return definition;
        } catch (Exception e) {
            throw new MirthApiException(e);
        }
    }

    private String getWsdlUrl(String channelId, String channelName, String wsdlUrl, String username, String password) throws Exception {
        wsdlUrl = templateValueReplacer.replaceValues(wsdlUrl, channelId, channelName);
        username = templateValueReplacer.replaceValues(username, channelId, channelName);
        password = templateValueReplacer.replaceValues(password, channelId, channelName);

        var wsdlUri = new URI(wsdlUrl);

        // add the username:password to the URL if using authentication
        if (username != null
            && password != null
            && !username.isBlank()
            && !password.isBlank()
        ) {
            var hostWithCredentials = "%s:%s@%s".formatted(username, password, wsdlUri.getHost());
            if (wsdlUri.getPort() > -1) {
                hostWithCredentials += ":%d".formatted(wsdlUri.getPort());
            }

            wsdlUri = new URI(
                wsdlUri.getScheme(),
                hostWithCredentials,
                wsdlUri.getPath(),
                wsdlUri.getQuery(),
                wsdlUri.getFragment()
            );
        }

        return wsdlUri.toURL().toString();
    }

    private TLSConnectorProperties getTlsSenderProperties(WebServiceDispatcherProperties properties) {
        var oTlsPluginProperties = properties.getPluginProperties()
            .stream()
            .filter(TLSConnectorProperties.class::isInstance)
            .map(TLSConnectorProperties.class::cast)
            .findFirst();

        return oTlsPluginProperties.orElseThrow(IllegalStateException::new);
    }

    private void cacheWsdlInterfaces(String wsdlUrl, Definition definition) throws Exception {
        if (definition == null) {
            throw new Exception("Could not find any definitions in " + wsdlUrl);
        }

        var definitionServiceMap = new DefinitionServiceMap();

        // wat...
        Map<String, Map<String, Definition>> wsdlInterfaceServiceMap = new LinkedHashMap<>();

        var serviceMap = definition.getServices();
        if (serviceMap != null && !serviceMap.isEmpty()) {
            for (Object serviceObject : serviceMap.values()) {
                var service = (Service) serviceObject;
                log.debug("Service: {}", service);

                var definitionPortMap = new DefinitionServiceMap.DefinitionPortMap();
                Map<String, Definition> wsdlInterfacePortMap = new LinkedHashMap<>();

                var ports = service.getPorts();
                if (ports != null && !ports.isEmpty()) {
                    for (var portObject : ports.values()) {
                        var port = (Port) portObject;
                        var portQName = new QName(service.getQName().getNamespaceURI(), port.getName()).toString();

                        var locationURI = getLocationUri(port);

                        var operations = new ArrayList<String>();
                        for (Object bindingOperation : port.getBinding().getBindingOperations()) {
                            String operationName = ((BindingOperation) bindingOperation).getName();
                            operations.add(operationName);
                        }

                        List<String> actions = new ArrayList<String>();
                        if (port.getBinding().getBindingOperations() != null) {
                            for (Object bindOperationObject : port.getBinding().getBindingOperations()) {
                                var extensions = ((BindingOperation) bindOperationObject).getExtensibilityElements();
                                if (extensions != null) {
                                    for (Object extension : extensions) {
                                        var extElement = (ExtensibilityElement) extension;
                                        if (extElement instanceof SOAPOperation soapOp) {
                                            actions.add(soapOp.getSoapActionURI());
                                        } else if (extElement instanceof SOAP12Operation soapOp) {
                                            actions.add(soapOp.getSoapActionURI());
                                        }
                                    }
                                }
                            }
                            definitionPortMap.getMap().put(portQName, new DefinitionServiceMap.PortInformation(operations, actions, locationURI));
                            wsdlInterfacePortMap.put(portQName, definition);
                        }
                    }
                }
                definitionServiceMap.getMap().put(service.getQName().toString(), definitionPortMap);
                wsdlInterfaceServiceMap.put(service.getQName().toString(), wsdlInterfacePortMap);
            }
        }
        definitionCache.put(wsdlUrl, definitionServiceMap);
        wsdlInterfaceCache.put(wsdlUrl, wsdlInterfaceServiceMap);
    }

    private static String getLocationUri(Port port) {
        String locationURI = null;
        for (Object element : port.getExtensibilityElements()) {
            if (element instanceof SOAPAddress address) {
                locationURI = address.getLocationURI();
            } else if (element instanceof SOAP12Address soap12Address) {
                locationURI = soap12Address.getLocationURI();
            } else if (element instanceof HTTPAddress httpAddress) {
                locationURI = httpAddress.getLocationURI();
            } else if (element instanceof HTTPOperation httpOperation) {
                locationURI = httpOperation.getLocationURI();
            }
        }
        return locationURI;
    }
}
