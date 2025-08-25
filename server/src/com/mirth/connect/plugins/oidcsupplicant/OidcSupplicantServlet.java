package com.mirth.connect.plugins.oidcsupplicant;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.api.DontCheckAuthorized;
import com.mirth.connect.server.controllers.ExtensionController;

public class OidcSupplicantServlet extends MirthServlet implements OidcSupplicantServletInterface {

    private static final Logger logger = LogManager.getLogger(OidcSupplicantServlet.class);

    public OidcSupplicantServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        // Pass initLogin = false to allow public access to these endpoints (no authentication required)
        super(request, sc, OidcSupplicantServletInterface.PLUGIN_POINT, false);
    }

    @Override
    @DontCheckAuthorized
    public javax.ws.rs.core.Response startOidc() {
        try {
            Properties props = ExtensionController.getInstance().getPluginProperties("OIDC Supplicant");
            String clientId = props.getProperty("oidc_client_id", "");
            String authEndpoint = props.getProperty("oidc_authorization_endpoint", "");

            if (authEndpoint == null || authEndpoint.isEmpty()) {
                return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity("OIDC authorization endpoint not configured.").build();
            }

            // build redirect_uri pointing to this servlet's complete-oidc endpoint
            StringBuffer requestUrl = request.getRequestURL();
            String base = requestUrl.toString();
            // strip off path after /api/
            int idx = base.indexOf("/api/");
            if (idx > 0) {
                base = base.substring(0, idx);
            }
            String redirectUri = base + "/api/extensions/oidcsupplicant/complete-oidc";

            String state = Long.toHexString(Double.doubleToLongBits(Math.random()));

            StringBuilder sb = new StringBuilder();
            sb.append(authEndpoint);
            if (authEndpoint.indexOf('?') < 0) {
                sb.append('?');
            } else {
                sb.append('&');
            }
            sb.append("response_type=code");
            sb.append("&scope=openid");
            sb.append("&client_id=").append(URLEncoder.encode(clientId, StandardCharsets.UTF_8.toString()));
            sb.append("&redirect_uri=").append(URLEncoder.encode(redirectUri, StandardCharsets.UTF_8.toString()));
            sb.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8.toString()));

            String location = sb.toString();

            return javax.ws.rs.core.Response.seeOther(new URI(location)).build();
        } catch (Exception e) {
            logger.error("Error starting OIDC flow", e);
            throw new MirthApiException(e);
        }
    }

    @Override
    @DontCheckAuthorized
    public String completeOidc() {
        // Simple HTML that alerts the code parameter from the URL
        String html = "<html><head><meta charset=\"utf-8\"><title>OIDC Complete</title></head><body><h1 id=\"title\">Login complete</h1><script>window.handleError = function(message) { title.innerText = message; }; window.handleStatus = window.handleError; setTimeout(function() { var params = new URLSearchParams(window.location.search); try { window.mirthlogin.completeLogin('oidc', params.get('code')); } catch (err) { title.innerText = err.message; } }, 3000);</script></body></html>";
        return html;
    }
}
