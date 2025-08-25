package com.mirth.connect.plugins.oidcsupplicant;

public class OidcLoginResult {
    private String authorizationCode;
    private String state;
    private String redirectUrl;

    public OidcLoginResult() {
        // Serialization constructor
    }

    public OidcLoginResult(String authorizationCode, String state, String redirectUrl) {
        this.authorizationCode = authorizationCode;
        this.state = state;
        this.redirectUrl = redirectUrl;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getState() {
        return state;
    }

    public void setRedirectUrl(String redirectUrl) {
        this.redirectUrl = redirectUrl;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }
}