package com.mirth.connect.plugins.oidcsupplicant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.mirth.connect.client.core.api.BaseServletInterface;
import com.mirth.connect.client.core.api.MirthOperation;

@Path("/extensions/oidcsupplicant")
@Tag(name = "Extension Services")
public interface OidcSupplicantServletInterface extends BaseServletInterface {

    public static final String PLUGIN_POINT = "OIDC Supplicant";

    @GET
    @Path("/start-oidc")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Starts the OIDC authorization flow by redirecting to the provider.")
    @MirthOperation(name = "startOidc", display = "Start OIDC", permission = "")
    public Response startOidc();

    @GET
    @Path("/complete-oidc")
    @Produces(MediaType.TEXT_HTML)
    @Operation(summary = "Completes the OIDC flow and returns a small HTML page that alerts the code.")
    @MirthOperation(name = "completeOidc", display = "Complete OIDC", permission = "")
    public String completeOidc();

}
