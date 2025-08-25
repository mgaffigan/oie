package com.mirth.connect.plugins.oidcsupplicant;

/**
 * Holds configuration key names for the OIDC supplicant plugin.
 */
public final class OidcSupplicantProperties {
    private OidcSupplicantProperties() {}

    public static final String OIDC_CLIENT_ID = "oidc_client_id";
    public static final String OIDC_CLIENT_SECRET = "oidc_client_secret";
    public static final String OIDC_AUTHORIZATION_ENDPOINT = "oidc_authorization_endpoint";
    public static final String OIDC_TOKEN_ENDPOINT = "oidc_token_endpoint";
    public static final String OIDC_ALLOW_FALLBACK = "oidc_allow_fallback";
    public static final String OIDC_SCOPES = "oidc_scopes";
}
