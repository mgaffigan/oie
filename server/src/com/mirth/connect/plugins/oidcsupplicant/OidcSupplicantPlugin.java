package com.mirth.connect.plugins.oidcsupplicant;

import java.util.Properties;
import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;

/**
 * Minimal server-side plugin implementing ServicePlugin for the OIDC supplicant.
 */
public class OidcSupplicantPlugin implements ServicePlugin {

    @Override
    public void init(Properties properties) {
        // placeholder - initialize plugin with persisted properties
    }

    @Override
    public void update(Properties properties) {
        // placeholder - handle properties updates
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
}
