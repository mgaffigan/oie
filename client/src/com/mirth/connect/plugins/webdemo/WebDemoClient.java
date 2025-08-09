package com.mirth.connect.plugins.webdemo;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.plugins.SettingsPanelPlugin;
import com.mirth.connect.plugins.webdemo.net.OieServerURLStreamHandlerFactory;

import java.net.URL;

public class WebDemoClient extends SettingsPanelPlugin {

    public WebDemoClient(String name) {
        super(name);
        
        URL.setURLStreamHandlerFactory(new OieServerURLStreamHandlerFactory());
    }

    @Override
    public AbstractSettingsPanel getSettingsPanel() {
        return new WebDemoPanel();
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
