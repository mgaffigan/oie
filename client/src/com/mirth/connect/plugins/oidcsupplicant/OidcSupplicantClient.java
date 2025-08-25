package com.mirth.connect.plugins.oidcsupplicant;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.plugins.SettingsPanelPlugin;

public class OidcSupplicantClient extends SettingsPanelPlugin {

    public OidcSupplicantClient(String name) {
        super(name);
    }

    @Override
    public AbstractSettingsPanel getSettingsPanel() {
        return new OidcSupplicantPanel(this);
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void reset() {}

    @Override
    public String getPluginPointName() {
        return "OIDC Supplicant";
    }
}
