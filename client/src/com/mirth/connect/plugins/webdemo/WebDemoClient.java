package com.mirth.connect.plugins.webdemo;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.plugins.SettingsPanelPlugin;

public class WebDemoClient extends SettingsPanelPlugin {

    private AbstractSettingsPanel panel;

    public WebDemoClient(String name) {
        super(name);
        panel = new WebDemoPanel("Web Demo", this);
    }

    @Override
    public AbstractSettingsPanel getSettingsPanel() {
        return panel;
    }

    @Override
    public void start() {}

    @Override
    public void stop() {}

    @Override
    public void reset() {}

    @Override
    public String getPluginPointName() {
        return "Web Demo";
    }
}
