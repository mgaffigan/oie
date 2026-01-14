/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.shared.servlet;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthApiProvider;
import com.kaurpalang.mirth.annotationsplugin.type.ApiProviderType;
import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.Operation;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;
import com.mirth.connect.connectors.http.HttpDispatcherProperties;
import com.mirth.connect.connectors.tcp.TcpDispatcherProperties;
import com.mirth.connect.connectors.ws.DefinitionServiceMap;
import com.mirth.connect.connectors.ws.WebServiceDispatcherProperties;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.openintegrationengine.tlsmanager.shared.TLSPluginConstants;
import org.openintegrationengine.tlsmanager.shared.models.ConnectionTestResult;
import org.openintegrationengine.tlsmanager.shared.models.LocalCertificate;
import org.openintegrationengine.tlsmanager.shared.models.TrustedCertificate;

import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_OCTET_STREAM;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;

@Path("/tlsmanager")
@Tag(name = TLSPluginConstants.PLUGIN_POINTNAME)
@Consumes({APPLICATION_XML, APPLICATION_JSON})
@Produces({APPLICATION_XML, APPLICATION_JSON})
@MirthApiProvider(type = ApiProviderType.SERVLET_INTERFACE)
public interface TLSServletInterface extends BaseServletInterface {

    @GET
    @Path("/importedcertificates")
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    @ApiResponse(responseCode = "200", description = "Found the information",
        content = {
            @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = Set.class)),
            @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = Set.class))
        })
    @MirthOperation(
        name = "getImportedCertificates",
        display = "Get list of imported certificates",
        type = Operation.ExecuteType.ASYNC
    )
    Set<String> getPublicCertificates();

    @GET
    @Path("/clientcertificates")
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    @ApiResponse(responseCode = "200", description = "Found the information",
        content = {
            @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = Set.class)),
            @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = Set.class))
        })
    @MirthOperation(
        name = "getClientCertificates",
        display = "Get list of client certificates",
        type = Operation.ExecuteType.ASYNC
    )
    Set<String> getClientCertificates();

    @GET
    @Path("/keystore")
    @Produces({APPLICATION_OCTET_STREAM})
    @ApiResponse(
        responseCode = "200",
        description = "Retrieve current additional keystore as byte array",
        content = {
            @Content(mediaType = APPLICATION_OCTET_STREAM, schema = @Schema(type = "string", format = "binary")),
        })
    @MirthOperation(
        name = "getTlsKeystore",
        display = "Retrieve current additional keystore as byte array",
        type = Operation.ExecuteType.ASYNC
    )
    byte[] getKeystore();

    @POST
    @Path("/truststore")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Overwrite the in use truststore"
    )
    @MirthOperation(
        name = "setTlsKeystore",
        display = "Write the additional truststore from the given byte array",
        type = Operation.ExecuteType.ASYNC
    )
    String setTruststore(
        @Param("inputStream")
        @Parameter(
            description = "The truststore file to upload.",
            schema = @Schema(description = "The truststore file to upload.", type = "string", format = "binary")
        )
        @FormDataParam("file") InputStream inputStream,

        @Param("password")
        @Parameter(description = "Truststore password")
        @Schema(description = "Truststore password", type = "string")
        @FormDataParam("password")
        String password
    ) throws ClientException;

    @GET
    @Path("/systemCertificates")
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    @ApiResponse(
        responseCode = "200",
        description = "Retrieve certificates from system truststore",
        content = {
            @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = List.class)),
            @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = List.class))
        })
    @MirthOperation(
        name = "getSystemCertificates",
        display = "Get the certificates from the system truststore",
        type = Operation.ExecuteType.ASYNC
    )
    List<TrustedCertificate> getSystemCertificates();

    @GET
    @Path("/localCertificates")
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    @ApiResponse(
        responseCode = "200",
        description = "Retrieve certificate/key pairs from current additional keystore",
        content = {
            @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = List.class)),
            @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = List.class))
        })
    @MirthOperation(
        name = "getLocalCertificates",
        display = "Get the certificate/key pairs from the keystore",
        type = Operation.ExecuteType.ASYNC
    )
    List<TrustedCertificate> getLocalCertificates();

    @PUT
    @Path("/localCertificates")
    @Consumes({APPLICATION_XML, APPLICATION_JSON})
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Overwrite the local certificates within the in use keystore"
    )
    @MirthOperation(
        name = "setLocalCertificates",
        display = "Write the keystore from the given certificate/key pair list",
        type = Operation.ExecuteType.ASYNC
    )
    void setLocalCertificates(
        @Param("localCertificates")
        @RequestBody(description = "The list of certificate/key pairs to write to the keystore.", required = true, content = {
            @Content(mediaType = MediaType.APPLICATION_XML),
            @Content(mediaType = MediaType.APPLICATION_JSON)
        })
        List<LocalCertificate> localCertificates
    );

    @GET
    @Path("/trustedCertificates")
    @Produces({APPLICATION_XML, APPLICATION_JSON})
    @ApiResponse(
        responseCode = "200",
        description = "Retrieve certificates from current additional truststore",
        content = {
            @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = List.class)),
            @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = List.class))
        })
    @MirthOperation(
        name = "getTrustedCertificates",
        display = "Get the certificates from the truststore",
        type = Operation.ExecuteType.ASYNC
    )
    List<TrustedCertificate> getTrustedCertificates();

    @PUT
    @Path("/trustedCertificates")
    @Consumes({APPLICATION_XML, APPLICATION_JSON})
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Overwrite the trusted certificates within the in use truststore"
    )
    @MirthOperation(
        name = "setTrustedCertificates",
        display = "Write the additional truststore from the given certificate list",
        type = Operation.ExecuteType.ASYNC
    )
    void setTrustedCertificates(
        @Param("trustedCertificates")
        @RequestBody(description = "The list of certificates to write to the truststore.", required = true, content = {
            @Content(mediaType = MediaType.APPLICATION_XML),
            @Content(mediaType = MediaType.APPLICATION_JSON)
        })
        List<TrustedCertificate> trustedCertificates
    );

    @GET
    @Path("/remoteCertificates")
    @ApiResponse(
        responseCode = "200",
        description = "Retrieve certificates served from a URL",
        content = {
            @Content(mediaType = APPLICATION_JSON, schema = @Schema(implementation = List.class)),
            @Content(mediaType = APPLICATION_XML, schema = @Schema(implementation = List.class))
        })
    @MirthOperation(
        name = "getRemoteCertificates",
        display = "Retrieve the list of certificates served at a certain URL",
        type = Operation.ExecuteType.ASYNC
    )
    List<TrustedCertificate> getRemoteCertificates(
        @Param("url")
        @Parameter(
            description = "The URL which to query for served certificates",
            schema = @Schema(type = "string")
        )
        @QueryParam("url") String url
    );

    @POST
    @Path("/testTcpConnection")
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Tests whether a connection can be successfully established to the destination endpoint."
    )
    @ApiResponse(
        content = {@Content(
            mediaType = "application/xml",
            examples = {@ExampleObject(
                name = "connection_test_response_http",
                ref = "../apiexamples/connection_test_response_http_xml"
            )}
        ), @Content(
            mediaType = "application/json",
            examples = {@ExampleObject(
                name = "connection_test_response_http",
                ref = "../apiexamples/connection_test_response_http_json"
            )}
        )}
    )
    @MirthOperation(
        name = "testTcpConnection",
        display = "Test TLS Connection in TCP Senders",
        type = Operation.ExecuteType.ASYNC,
        auditable = false
    )
    ConnectionTestResult testTcpConnection(
        @Param("channelId")
        @Parameter(description = "The ID of the channel.", required = true)
        @QueryParam("channelId") String channelId,
        @Param("channelName")
        @Parameter(description = "The name of the channel.", required = true)
        @QueryParam("channelName") String channelName,
        @Param("properties")
        @RequestBody(description = "The TCP Sender properties to use.", required = true, content = {
            @Content(
                mediaType = "application/xml",
                examples = {
                    @ExampleObject(name = "http_dispatcher_properties", ref = "../apiexamples/http_dispatcher_properties_xml")
                }
            ),
            @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "http_dispatcher_properties", ref = "../apiexamples/http_dispatcher_properties_json")
                }
            )
        }) TcpDispatcherProperties httpDispatcherProperties
    ) throws ClientException;

    @POST
    @Path("/testHttpsConnection")
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Tests whether a connection can be successfully established to the destination endpoint."
    )
    @ApiResponse(
        content = {@Content(
            mediaType = "application/xml",
            examples = {@ExampleObject(
                name = "connection_test_response_http",
                ref = "../apiexamples/connection_test_response_http_xml"
            )}
        ), @Content(
            mediaType = "application/json",
            examples = {@ExampleObject(
                name = "connection_test_response_http",
                ref = "../apiexamples/connection_test_response_http_json"
            )}
        )}
    )
    @MirthOperation(
        name = "testHttpsConnection",
        display = "Test TLS Connection in HTTP Senders",
        type = Operation.ExecuteType.ASYNC,
        auditable = false
    )
    ConnectionTestResult testHttpsConnection(
        @Param("channelId")
        @Parameter(description = "The ID of the channel.", required = true)
        @QueryParam("channelId") String channelId,
        @Param("channelName")
        @Parameter(description = "The name of the channel.", required = true)
        @QueryParam("channelName") String channelName,
        @Param("properties")
        @RequestBody(description = "The HTTP Sender properties to use.", required = true, content = {
            @Content(
                mediaType = "application/xml",
                examples = {
                    @ExampleObject(name = "http_dispatcher_properties", ref = "../apiexamples/http_dispatcher_properties_xml")
                }
            ),
            @Content(
                mediaType = "application/json",
                examples = {
                    @ExampleObject(name = "http_dispatcher_properties", ref = "../apiexamples/http_dispatcher_properties_json")
                }
            )
        }) HttpDispatcherProperties httpDispatcherProperties
    ) throws ClientException;

    @POST
    @Path("/testWsConnection")
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Tests whether a connection can be successfully established to the destination endpoint."
    )
    @ApiResponse(
        content = {@Content(
            mediaType = "application/xml",
            examples = {@ExampleObject(
                name = "connection_test_response_http",
                ref = "../apiexamples/connection_test_response_http_xml"
            )}
        ), @Content(
            mediaType = "application/json",
            examples = {@ExampleObject(
                name = "connection_test_response_http",
                ref = "../apiexamples/connection_test_response_http_json"
            )}
        )}
    )
    @MirthOperation(
        name = "testWsConnection",
        display = "Test TLS Connection in Web Service Sender",
        type = Operation.ExecuteType.ASYNC,
        auditable = false
    )
    ConnectionTestResult testWsConnection(
        @Param("channelId")
        @Parameter(description = "The ID of the channel.", required = true)
        @QueryParam("channelId") String channelId,
        @Param("channelName")
        @Parameter(description = "The name of the channel.", required = true)
        @QueryParam("channelName") String channelName,
        @Param("properties")
        @RequestBody(description = "The WebService Sender properties to use.", required = true) WebServiceDispatcherProperties wsDispatcherProperties
    ) throws ClientException;

    @POST
    @Path("/_cacheWsdlFromUrl")
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Downloads the WSDL at the specified URL and caches the web service definition tree."
    )
    @MirthOperation(
        name = "cacheWsdlFromUrl",
        display = "Download and cache WSDL",
        type = Operation.ExecuteType.ASYNC,
        auditable = false
    )
    Object cacheWsdlFromUrl(
        @Param("channelId")
        @Parameter(description = "The ID of the channel.", required = true)
        @QueryParam("channelId")
        String channelId,

        @Param("channelName")
        @Parameter(description = "The name of the channel.")
        @QueryParam("channelName")
        String channelName,

        @Param("properties")
        @RequestBody(
            description = "The Web Service Sender properties to use. These properties can be found in the exported channel's XML file. Copy the data from the opening tag &lt;destinationConnectorProperties&gt; to the closing tag &lt;/wsdlDefinitionMap&gt; (including the tags). Paste over the information below between the opening and closing tags for &lt;com.mirth.connect.connectors.ws.WebServiceDispatcherProperties&gt;.",
            required = true,
            content = {@Content(mediaType = "application/xml"), @Content(mediaType = "application/json")}
        ) WebServiceDispatcherProperties properties
    ) throws ClientException;

    @POST
    @Path("/_isWsdlCached")
    @Consumes({"application/x-www-form-urlencoded"})
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Returns true if the definition tree for the WSDL is cached by the server."
    )
    @MirthOperation(
        name = "isWsdlCached",
        display = "Check if WSDL is cached",
        type = Operation.ExecuteType.ASYNC,
        auditable = false
    )
    boolean isWsdlCached(
        @Param("channelId")
        @Parameter(description = "The ID of the channel.", required = true, schema = @Schema(description = "The ID of the channel."))
        @FormParam("channelId")
        String channelId,

        @Param("channelName")
        @Parameter(description = "The name of the channel.", schema = @Schema(description = "The name of the channel."))
        @FormParam("channelName")
        String channelName,

        @Param("wsdlUrl")
        @Parameter(description = "The full URL to the WSDL describing the web service method to be called.", required = true, schema = @Schema(description = "The full URL to the WSDL describing the web service method to be called."))
        @FormParam("wsdlUrl")
        String wsdlUrl,

        @Param("username")
        @Parameter(description = "Username used to authenticate to the web server.", schema = @Schema(description = "Username used to authenticate to the web server."))
        @FormParam("username")
        String username,

        @Param(value = "password", excludeFromAudit = true)
        @Parameter(description = "Password used to authenticate to the web server.", schema = @Schema(description = "Password used to authenticate to the web server."))
        @FormParam("password")
        String password
    ) throws ClientException;

    @POST
    @Path("/getDefinition")
    @Consumes({"application/x-www-form-urlencoded"})
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Retrieves the definition service map corresponding to the specified WSDL."
    )
    @MirthOperation(
        name = "getDefinition",
        display = "Get WSDL Definition",
        type = Operation.ExecuteType.ASYNC,
        auditable = false
    )
    DefinitionServiceMap getDefinition(
        @Param("channelId")
        @Parameter(description = "The ID of the channel.", required = true, schema = @Schema(description = "The ID of the channel."))
        @FormParam("channelId")
        String channelId,

        @Param("channelName")
        @Parameter(description = "The name of the channel.", schema = @Schema(description = "The name of the channel."))
        @FormParam("channelName")
        String channelName,

        @Param("wsdlUrl") @Parameter(description = "The full URL to the WSDL describing the web service method to be called.", required = true, schema = @Schema(description = "The full URL to the WSDL describing the web service method to be called."))
        @FormParam("wsdlUrl")
        String wsdlUrl,

        @Param("username") @Parameter(description = "Username used to authenticate to the web server.", schema = @Schema(description = "Username used to authenticate to the web server."))
        @FormParam("username")
        String username,

        @Param(value = "password",excludeFromAudit = true)
        @Parameter(description = "Password used to authenticate to the web server.", schema = @Schema(description = "Password used to authenticate to the web server."))
        @FormParam("password")
        String password
    ) throws ClientException;
}
