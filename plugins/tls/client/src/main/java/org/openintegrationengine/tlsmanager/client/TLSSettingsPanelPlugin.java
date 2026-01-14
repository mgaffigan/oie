/*
 * SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2025 NovaMap Health Limited <https://novamap.health>
 */

package org.openintegrationengine.tlsmanager.client;

import com.kaurpalang.mirth.annotationsplugin.annotation.MirthClientClass;
import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.plugins.SettingsPanelPlugin;
import org.openintegrationengine.tlsmanager.client.panel.TLSManagerSettingsPanel;
import org.openintegrationengine.tlsmanager.shared.TLSPluginConstants;

@MirthClientClass
public class TLSSettingsPanelPlugin extends SettingsPanelPlugin {

    private final AbstractSettingsPanel settingsPanel;

    public TLSSettingsPanelPlugin(String name) {
        super(name);

        settingsPanel = new TLSManagerSettingsPanel("TLS Manager", this);
    }

    @Override
    public AbstractSettingsPanel getSettingsPanel() {
        return settingsPanel;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void reset() {
    }

    @Override
    public String getPluginPointName() {
        return TLSPluginConstants.TLS_TASK_PLUGIN_POINT_NAME;
    }
}
