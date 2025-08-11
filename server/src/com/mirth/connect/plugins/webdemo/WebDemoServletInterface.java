package com.mirth.connect.plugins.webdemo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.Operation.ExecuteType;
import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;
import com.mirth.connect.client.core.api.Param;

@Path("/extensions/webdemo")
@Tag(name = "Extension Services")
@Consumes({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
@Produces({ MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON })
public interface WebDemoServletInterface extends BaseServletInterface {

    public static final String PLUGIN_POINT = "Web Demo";
    public static final String PERMISSION_USE = "Use Web Demo";

    @GET
    @Path("/ping")
    @Operation(summary = "Simple liveness check that returns 'pong'.")
    @ApiResponse(content = {
            @Content(mediaType = MediaType.APPLICATION_XML, examples = {
                    @ExampleObject(name = "pongXml", value = "<map><entry><string>status</string><string>pong</string></entry></map>") }),
            @Content(mediaType = MediaType.APPLICATION_JSON, examples = {
                    @ExampleObject(name = "pongJson", value = "{\n  \"status\": \"pong\"\n}") }) })
    @MirthOperation(name = "ping", display = "Ping Web Demo", permission = PERMISSION_USE, type = ExecuteType.ASYNC, auditable = false)
    public String ping() throws ClientException;

    @GET
    @Path("/echo")
    @Operation(summary = "Echoes back the provided message along with server time.")
    @ApiResponse(content = {
            @Content(mediaType = MediaType.APPLICATION_XML, examples = {
                    @ExampleObject(name = "echoXml", value = "<map>\n  <entry><string>message</string><string>Hello</string></entry>\n  <entry><string>serverTime</string><string>2025-01-01T00:00:00Z</string></entry>\n</map>") }),
            @Content(mediaType = MediaType.APPLICATION_JSON, examples = {
                    @ExampleObject(name = "echoJson", value = "{\n  \"message\": \"Hello\",\n  \"serverTime\": \"2025-01-01T00:00:00Z\"\n}") }) })
    @MirthOperation(name = "echo", display = "Echo message (Web Demo)", permission = PERMISSION_USE, type = ExecuteType.ASYNC, auditable = false)
    public Map<String, String> echo(
            @Param("message") @Parameter(description = "The message to echo back.") @QueryParam("message") String message) throws ClientException;
}
