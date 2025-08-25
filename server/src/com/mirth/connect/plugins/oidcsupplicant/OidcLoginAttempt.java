package com.mirth.connect.plugins.oidcsupplicant;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Properties;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.UserController;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.model.User;

public class OidcLoginAttempt {
    private Properties properties;

    private final Logger logger = LoggerFactory.getLogger(OidcLoginAttempt.class);

    public OidcLoginAttempt(Properties properties) {
        this.properties = properties;
    }

    public LoginStatus login(String loginResultJson) {
        OidcLoginResult loginResult = parseLoginResult(loginResultJson);
        if (loginResult == null) {
            return new LoginStatus(LoginStatus.Status.FAIL, "Failed to parse login result");
        }

        String tokenJson = getTokenFromIdp(loginResult);
        if (tokenJson == null) {
            return new LoginStatus(LoginStatus.Status.FAIL, "Failed to obtain token");
        }

        JsonNode tokenNode = getIdToken(tokenJson);
        if (tokenNode == null) {
            return new LoginStatus(LoginStatus.Status.FAIL, "Failed to parse ID token");
        }

        User user = getOrCreateUser(tokenNode);
        if (user == null) {
            return new LoginStatus(LoginStatus.Status.FAIL, "Failed to create user");
        }

        return new LoginStatus(LoginStatus.Status.SUCCESS, "Login successful", user.getUsername());
    }

    private OidcLoginResult parseLoginResult(String loginResultJson) {
        try {
            return new ObjectMapper().readValue(loginResultJson, OidcLoginResult.class);
        } catch (Exception e) {
            logger.error("Error parsing login result JSON", e);
            return null;
        }
    }

    private String getTokenFromIdp(OidcLoginResult loginResult) {
        try (CloseableHttpClient httpClient = HttpClients.custom().build()) {
            HttpPost httpPost = new HttpPost(properties.getProperty(OidcSupplicantProperties.OIDC_TOKEN_ENDPOINT));
            httpPost.setEntity(new UrlEncodedFormEntity(Arrays.asList(
                new BasicNameValuePair("grant_type", "authorization_code"),
                new BasicNameValuePair("code", loginResult.getAuthorizationCode()),
                new BasicNameValuePair("redirect_uri", loginResult.getRedirectUrl()),
                new BasicNameValuePair("client_id", properties.getProperty(OidcSupplicantProperties.OIDC_CLIENT_ID)),
                new BasicNameValuePair("client_secret", properties.getProperty(OidcSupplicantProperties.OIDC_CLIENT_SECRET))
            ), StandardCharsets.UTF_8));

            HttpResponse response = httpClient.execute(httpPost);
            String responseString = EntityUtils.toString(response.getEntity());
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                logger.error("Failed to obtain token, status code: {}, response: {}", statusCode, responseString);
                return null;
            }

            logger.info("Response from token endpoint: {}", responseString);
            return responseString;
        } catch (Exception e) {
            logger.error("Error obtaining token from IdP", e);
            return null;
        }
    }

    private JsonNode getIdToken(String tokenJson) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(tokenJson);
            String idTokenJwt = rootNode.path("id_token").asText();

            // Split and parse the JWT body
            // (no validation of the token is made since it was directly received from the IdP)
            // (see OIDC 3.1.3.7.6)
            String[] jwtParts = idTokenJwt.split("\\.");
            if (jwtParts.length != 3) {
                logger.error("Invalid JWT format");
                return null;
            }

            String payload = new String(Base64.getUrlDecoder().decode(jwtParts[1]), StandardCharsets.UTF_8);
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            logger.error("Error parsing token JSON", e);
            return null;
        }
    }

    private User getOrCreateUser(JsonNode idToken) {
        try {
            UserController uc = ControllerFactory.getFactory().createUserController();

            String username = getRequiredMappedProperty(idToken, "sub");
            User user = uc.getUser(null, username);
            if (user == null) {
                logger.info("Creating new user for OIDC login: {}", username);
                user = new User();
                user.setUsername(username);
                user.setEmail(getMappedProperty(idToken, "email"));
                user.setFirstName(getMappedProperty(idToken, "given_name"));
                user.setLastName(getMappedProperty(idToken, "family_name"));
                uc.updateUser(user);
            }
            return user;
        } catch (Exception e) {
            logger.error("Error creating or retrieving user", e);
            return null;
        }
    }

    private String getMappedProperty(JsonNode idToken, String property) {
        String remappedName = properties.getProperty("oidc_" + property + "_claim");
        if (remappedName != null && !remappedName.isEmpty()) {
            property = remappedName;
        }
        return idToken.path(property).asText();
    }

    private String getRequiredMappedProperty(JsonNode idToken, String property) {
        String value = getMappedProperty(idToken, property);
        if (value == null) {
            throw new IllegalArgumentException("Missing required claim: " + property);
        }
        return value;
    }
}