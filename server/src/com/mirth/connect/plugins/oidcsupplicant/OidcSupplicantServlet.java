package com.mirth.connect.plugins.oidcsupplicant;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

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
            String clientId = props.getProperty(OidcSupplicantProperties.OIDC_CLIENT_ID, "");
            String authEndpoint = props.getProperty(OidcSupplicantProperties.OIDC_AUTHORIZATION_ENDPOINT, "");

            if (authEndpoint == null || authEndpoint.isEmpty()) {
                return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST).entity("OIDC authorization endpoint not configured.").build();
            }


            String redirectUrl = getRedirectUrl();
            String state = getCsrfToken();

            request.getSession(true).setAttribute("oidc_csrf_state", state);

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
            sb.append("&redirect_uri=").append(URLEncoder.encode(redirectUrl, StandardCharsets.UTF_8.toString()));
            sb.append("&state=").append(URLEncoder.encode(state, StandardCharsets.UTF_8.toString()));

            String location = sb.toString();

            return javax.ws.rs.core.Response.seeOther(new URI(location)).build();
        } catch (Exception e) {
            logger.error("Error starting OIDC flow", e);
            throw new MirthApiException(e);
        }
    }

    private String getCsrfToken() {
        byte[] randomBytes = new byte[32];
        java.security.SecureRandom secureRandom = new java.security.SecureRandom();
        secureRandom.nextBytes(randomBytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String getRedirectUrl() {
        // build redirect_uri pointing to this servlet's complete-oidc endpoint
        StringBuffer requestUrl = request.getRequestURL();
        String base = requestUrl.toString();
        // strip off path after /api/
        int idx = base.indexOf("/api/");
        if (idx > 0) {
            base = base.substring(0, idx);
        }
        return base + "/api/extensions/oidcsupplicant/complete-oidc";
    }

    @Override
    @DontCheckAuthorized
    public String completeOidc() {
        logger.info("Completing OIDC flow");

        String code = request.getParameter("code");
        logger.info("Received OIDC authorization code: {}", code);
        if (code == null || code.isEmpty()) {
            logger.error("Authorization code not found in request");
            throw new MirthApiException("Authorization code not found in request");
        }

        // Retrieve the CSRF state from the session
        String state = (String) request.getSession().getAttribute("oidc_csrf_state");
        logger.info("Received OIDC CSRF state: {}", state);
        request.getSession().removeAttribute("oidc_csrf_state");
        if (!state.equals(request.getParameter("state"))) {
            logger.error("Invalid CSRF state");
            throw new MirthApiException("Invalid CSRF state");
        }

        OidcLoginResult loginResult = new OidcLoginResult(code, state, getRedirectUrl());
        logger.info("Generated OIDC login result: {}", loginResult);
        String html = getResourceAsString("resources/complete.html");
        logger.info("Generated HTML for OIDC login result");
        try {
            html = html.replace("{LOGIN_RESULT}", new ObjectMapper().writeValueAsString(loginResult));
        } catch (Exception e) {
            logger.error("Error generating login result", e);
            throw new MirthApiException("Error generating login result");
        }
        return html;
    }

    private String getResourceAsString(String path) {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
            logger.error("Resource not found: {}", path);
            throw new MirthApiException("Resource not found: " + path);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = is.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            logger.error("Error reading resource: {}", path, e);
            throw new MirthApiException("Error reading resource: " + path);
        }
    }
}
