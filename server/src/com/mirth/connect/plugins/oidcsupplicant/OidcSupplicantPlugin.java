package com.mirth.connect.plugins.oidcsupplicant;

import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import com.mirth.connect.client.core.ControllerException;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.model.LoginStatus;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.plugins.WebAuthorizationPlugin;

public class OidcSupplicantPlugin implements ServicePlugin, WebAuthorizationPlugin {

    private Properties properties;

    @Override
    public void init(Properties properties) {
        this.properties = properties;
    }

    @Override
    public void update(Properties properties) {
        this.properties = properties;
    }

    @Override
    public Properties getDefaultProperties() {
        Properties defaults = new Properties();
        // set sensible defaults (empty for now)
        defaults.setProperty(OidcSupplicantProperties.OIDC_CLIENT_ID, "");
        defaults.setProperty(OidcSupplicantProperties.OIDC_CLIENT_SECRET, "");
        defaults.setProperty(OidcSupplicantProperties.OIDC_AUTHORIZATION_ENDPOINT, "");
        defaults.setProperty(OidcSupplicantProperties.OIDC_TOKEN_ENDPOINT, "");
        return defaults;
    }

    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        // No permissions required at this time.
        return new ExtensionPermission[0];
    }

    @Override
    public String getPluginPointName() {
        return "OIDC Supplicant";
    }

    @Override
    public void start() {
        // no-op for now
    }

    @Override
    public void stop() {
        // no-op for now
    }

    public boolean isConfigured() {
        return properties != null
            && StringUtils.isNotBlank(properties.getProperty(OidcSupplicantProperties.OIDC_CLIENT_ID));
    }

    public LoginStatus authorizeUser(String username, String plainPassword) throws ControllerException {
        if (!username.equals("oidc")) return null;
        if (!isConfigured()) {
            return new LoginStatus(LoginStatus.Status.FAIL, "OIDC authentication not configured");
        }

        return new OidcLoginAttempt(properties).login(plainPassword);
    }

    public String[] getExtraLaunchArgs(String baseUrl) {
        if (!isConfigured()) return null;
        return new String[] { "weblogin", baseUrl + "/api/extensions/oidcsupplicant/start-oidc" };
    }
}
